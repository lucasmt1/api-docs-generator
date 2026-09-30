package dev.apidocs.core.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.model.ProjectInfo;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Reads project metadata from the build file and the Spring application configuration. */
public final class ProjectInfoReader {

    private static final Set<String> DATABASE_DRIVERS = Set.of("h2", "postgresql", "mysql-connector-j",
            "mariadb-java-client", "mssql-jdbc", "ojdbc8", "ojdbc11", "mongodb-driver-sync", "hsqldb");
    private static final YAMLMapper YAML = new YAMLMapper();

    private static final Pattern BOOT_PLUGIN = Pattern.compile(
            "id\\s*\\(?\\s*[\"']org\\.springframework\\.boot[\"']\\s*\\)?\\s*version\\s*[\"']([^\"']+)[\"']");
    private static final Pattern JAVA_TOOLCHAIN = Pattern.compile("JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)");
    private static final Pattern SOURCE_COMPATIBILITY = Pattern.compile(
            "sourceCompatibility\\s*=\\s*(?:JavaVersion\\.VERSION_)?['\"]?(\\d+)");
    /** Group 1 is the Gradle configuration (implementation, testImplementation...), group 2 the artifactId. */
    private static final Pattern DEPENDENCY = Pattern.compile(
            "(\\w+)\\s*\\(?\\s*[\"'][\\w.\\-]+:([\\w.\\-]+)(?::[^\"']*)?[\"']");
    private static final Pattern ROOT_NAME = Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern DESCRIPTION = Pattern.compile("(?m)^\\s*description\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*[\"']([^\"']+)[\"']");
    /** An innermost {@code ${NAME:default}} placeholder; group 1 is the default value. */
    private static final Pattern PLACEHOLDER_WITH_DEFAULT = Pattern.compile("\\$\\{[^:}$]+:([^}$]*)}");

    public ProjectInfo read(Path projectDir) {
        Path root = projectDir.toAbsolutePath().normalize();
        BuildInfo build;
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            build = readPom(root.resolve("pom.xml"));
        } else {
            build = gradleFile(root).map(file -> readGradle(root, file)).orElseGet(() -> BuildInfo.unknown(root));
        }
        Map<String, String> config = readApplicationConfig(root);
        return new ProjectInfo(build.name(), build.description(), build.version(), build.javaVersion(),
                build.springBootVersion(), build.dependencies(),
                configValue(config, "spring.application.name"),
                normalizeContextPath(configValue(config, "server.servlet.context-path")),
                build.buildTool());
    }

    private record BuildInfo(String name, String description, String version, String javaVersion,
            String springBootVersion, List<String> dependencies, String buildTool) {

        static BuildInfo unknown(Path root) {
            return new BuildInfo(directoryName(root), "", "", "", "", List.of(), "unknown");
        }
    }

    private BuildInfo readPom(Path pom) {
        Element project;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        } catch (Exception e) {
            throw new AnalysisException("Cannot parse " + pom + ": " + e.getMessage(), e);
        }
        Map<String, String> properties = new HashMap<>();
        child(project, "properties").ifPresent(props ->
                children(props).forEach(p -> properties.put(p.getTagName(), p.getTextContent().strip())));
        Optional<Element> parent = child(project, "parent");

        String artifactId = text(project, "artifactId");
        String name = text(project, "name").isEmpty() ? artifactId : text(project, "name");
        String version = text(project, "version").isEmpty()
                ? parent.map(p -> text(p, "version")).orElse("")
                : text(project, "version");
        String javaVersion = firstNonEmpty(properties.get("java.version"),
                properties.get("maven.compiler.release"), properties.get("maven.compiler.source"));
        String springBoot = parent
                .filter(p -> text(p, "artifactId").equals("spring-boot-starter-parent"))
                .map(p -> text(p, "version"))
                .orElse("");
        if (springBoot.isEmpty()) {
            springBoot = child(project, "dependencyManagement")
                    .flatMap(dm -> child(dm, "dependencies"))
                    .flatMap(deps -> children(deps).stream()
                            .filter(d -> text(d, "artifactId").equals("spring-boot-dependencies"))
                            .map(d -> text(d, "version"))
                            .findFirst())
                    .orElse("");
        }
        List<String> dependencies = child(project, "dependencies")
                .map(deps -> children(deps).stream()
                        .filter(d -> !text(d, "scope").equals("test"))
                        .map(d -> text(d, "artifactId"))
                        .filter(ProjectInfoReader::isNotable)
                        .distinct()
                        .sorted()
                        .toList())
                .orElse(List.of());
        return new BuildInfo(resolve(name, properties), resolve(text(project, "description"), properties),
                resolve(version, properties), resolve(javaVersion, properties),
                resolve(springBoot, properties), dependencies, "maven");
    }

    private Optional<Path> gradleFile(Path root) {
        return Stream.of("build.gradle.kts", "build.gradle").map(root::resolve).filter(Files::isRegularFile).findFirst();
    }

    private BuildInfo readGradle(Path root, Path buildFile) {
        String build = readQuietly(buildFile);
        String settings = Stream.of("settings.gradle.kts", "settings.gradle").map(root::resolve)
                .filter(Files::isRegularFile).findFirst().map(ProjectInfoReader::readQuietly).orElse("");
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = DEPENDENCY.matcher(build);
        while (matcher.find()) {
            if (!matcher.group(1).startsWith("test") && isNotable(matcher.group(2))) {
                dependencies.add(matcher.group(2));
            }
        }
        return new BuildInfo(
                find(ROOT_NAME, settings).orElse(directoryName(root)),
                find(DESCRIPTION, build).orElse(""),
                find(VERSION, build).orElse(""),
                find(JAVA_TOOLCHAIN, build).or(() -> find(SOURCE_COMPATIBILITY, build)).orElse(""),
                find(BOOT_PLUGIN, build).orElse(""),
                dependencies.stream().distinct().sorted().toList(),
                "gradle");
    }

    private Map<String, String> readApplicationConfig(Path root) {
        Map<String, String> values = new HashMap<>();
        Path resources = root.resolve("src/main/resources");
        if (!hasApplicationConfig(resources)) {
            try (Stream<Path> modules = Files.list(root)) {
                Optional<Path> moduleResources = modules.filter(Files::isDirectory).sorted()
                        .map(module -> module.resolve("src/main/resources"))
                        .filter(ProjectInfoReader::hasApplicationConfig)
                        .findFirst();
                if (moduleResources.isPresent()) {
                    resources = moduleResources.get();
                }
            } catch (IOException ignored) {
                return values;
            }
        }
        Path properties = resources.resolve("application.properties");
        if (Files.isRegularFile(properties)) {
            Properties loaded = new Properties();
            try (Reader reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
                loaded.load(reader);
                loaded.forEach((key, value) -> values.put(key.toString(), value.toString().strip()));
            } catch (IOException ignored) {
                // unreadable configuration is not fatal
            }
        }
        for (String fileName : List.of("application.yml", "application.yaml")) {
            Path yaml = resources.resolve(fileName);
            if (Files.isRegularFile(yaml)) {
                try {
                    flatten("", YAML.readTree(yaml.toFile()), values);
                } catch (IOException ignored) {
                    // invalid YAML is not fatal
                }
            }
        }
        return values;
    }

    private static boolean hasApplicationConfig(Path resources) {
        return Stream.of("application.properties", "application.yml", "application.yaml")
                .anyMatch(name -> Files.isRegularFile(resources.resolve(name)));
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry ->
                    flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), out));
        } else if (node.isValueNode() && !node.isNull()) {
            out.putIfAbsent(prefix, node.asText());
        }
    }

    /**
     * Reads a configuration value, replacing {@code ${NAME:default}} placeholders by their default. A value that
     * still contains an unresolvable placeholder (such as {@code ${NAME}}) is treated as unset.
     */
    private static String configValue(Map<String, String> config, String key) {
        String value = config.getOrDefault(key, "").strip();
        String previous;
        do {
            previous = value;
            value = PLACEHOLDER_WITH_DEFAULT.matcher(value)
                    .replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        } while (!value.equals(previous));
        return value.contains("${") ? "" : value.strip();
    }

    static String normalizeContextPath(String raw) {
        String path = raw.strip();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isNotable(String artifactId) {
        return artifactId.startsWith("spring-boot-starter") || DATABASE_DRIVERS.contains(artifactId)
                || artifactId.equals("lombok");
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value.startsWith("${") && value.endsWith("}")) {
            return properties.getOrDefault(value.substring(2, value.length() - 1), "");
        }
        return value;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Optional<String> find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    private static String directoryName(Path root) {
        return root.getFileName() == null ? "project" : root.getFileName().toString();
    }

    private static Optional<Element> child(Element parent, String tag) {
        return children(parent).stream().filter(e -> e.getTagName().equals(tag)).findFirst();
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }

    private static String text(Element parent, String tag) {
        return child(parent, tag).map(e -> e.getTextContent().replaceAll("\\s+", " ").strip()).orElse("");
    }
}
