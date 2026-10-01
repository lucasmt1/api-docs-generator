package dev.apidocs.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.apidocs.core.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Reads {@code .apidocs.yml}; refuses secrets because the file is committed to the repository. */
public final class ProjectConfigLoader {

    public static final String FILE_NAME = ".apidocs.yml";

    private static final Set<String> SECRET_KEYS =
            Set.of("apikey", "token", "secret", "password", "accesstoken", "authorization");
    private static final YAMLMapper YAML = new YAMLMapper();

    public Optional<ProjectConfigFile> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = YAML.readTree(file.toFile());
        } catch (IOException e) {
            String reason = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            throw new ConfigException("Invalid " + file.getFileName() + ": " + reason, e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Optional.of(ProjectConfigFile.EMPTY);
        }
        if (!root.isObject()) {
            throw new ConfigException("Invalid " + file.getFileName() + ": expected a mapping at the top level");
        }
        rejectSecrets(root, "");
        return Optional.of(parse(root, file.getFileName().toString()));
    }

    /** Wrong-shaped values are errors: silently dropping e.g. an {@code exclude} would document hidden files. */
    private static ProjectConfigFile parse(JsonNode root, String fileName) {
        JsonNode context = mapping(root, "context", "context", fileName);
        JsonNode llm = mapping(root, "llm", "llm", fileName);
        Map<String, String> glossary = new TreeMap<>();
        mapping(context, "glossary", "context.glossary", fileName).properties().forEach(entry ->
                glossary.put(entry.getKey(),
                        text(entry.getValue(), "context.glossary." + entry.getKey(), fileName)));
        return new ProjectConfigFile(
                field(root, "language", "", fileName),
                new ContextConfig(field(context, "description", "context.", fileName),
                        field(context, "audience", "context.", fileName), glossary,
                        field(context, "instructions", "context.", fileName)),
                field(llm, "provider", "llm.", fileName), field(llm, "model", "llm.", fileName),
                field(llm, "baseUrl", "llm.", fileName), field(llm, "apiKeyEnv", "llm.", fileName),
                globs(root.path("exclude"), fileName));
    }

    private static void rejectSecrets(JsonNode node, String path) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                String childPath = path.isEmpty() ? key : path + "." + key;
                String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (SECRET_KEYS.contains(normalized)) {
                    throw new ConfigException("Secrets are not allowed in " + FILE_NAME + " (found '" + childPath
                            + "'). Put API keys in environment variables instead.");
                }
                rejectSecrets(entry.getValue(), childPath);
            });
        } else if (node.isArray()) {
            node.forEach(child -> rejectSecrets(child, path));
        }
    }

    /** The child mapping {@code field} of {@code parent}; an absent or null entry is an empty mapping. */
    private static JsonNode mapping(JsonNode parent, String field, String path, String fileName) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return MissingNode.getInstance();
        }
        if (!value.isObject()) {
            throw invalid(fileName, path + " must be a mapping");
        }
        return value;
    }

    private static String field(JsonNode parent, String field, String pathPrefix, String fileName) {
        return text(parent.path(field), pathPrefix + field, fileName);
    }

    /** A scalar as stripped text; absent, null and blank values mean "not set" (never the string "null"). */
    private static String text(JsonNode value, String path, String fileName) {
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (!value.isValueNode()) {
            throw invalid(fileName, path + " must be a text value");
        }
        return value.asText().strip();
    }

    private static List<String> globs(JsonNode node, String fileName) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid(fileName, "exclude must be a list of glob strings");
        }
        List<String> exclude = new ArrayList<>();
        for (JsonNode element : node) {
            String glob = element.isValueNode() && !element.isNull() ? element.asText().strip() : "";
            if (glob.isEmpty()) {
                throw invalid(fileName, "exclude must be a list of glob strings");
            }
            exclude.add(glob);
        }
        return exclude;
    }

    private static ConfigException invalid(String fileName, String detail) {
        return new ConfigException("Invalid " + fileName + ": " + detail);
    }
}
