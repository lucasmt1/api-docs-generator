package dev.apidocs.core.source;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectInfoReaderTest {

    @TempDir
    Path dir;

    private void write(String relative, String content) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void readsMavenProjectAndYamlConfiguration() throws IOException {
        write("pom.xml", """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>4.1.1</version>
                  </parent>
                  <artifactId>orders</artifactId>
                  <version>1.2.0</version>
                  <name>Orders API</name>
                  <description>
                    Handles   orders.
                  </description>
                  <properties><java.version>21</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
                    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
                    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """);
        write("src/main/resources/application.yml", """
                spring:
                  application:
                    name: orders-api
                server:
                  servlet:
                    context-path: /shop/
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.name()).isEqualTo("Orders API");
        assertThat(info.description()).isEqualTo("Handles orders.");
        assertThat(info.version()).isEqualTo("1.2.0");
        assertThat(info.javaVersion()).isEqualTo("21");
        assertThat(info.springBootVersion()).isEqualTo("4.1.1");
        assertThat(info.dependencies()).containsExactly("h2", "lombok", "spring-boot-starter-webmvc");
        assertThat(info.applicationName()).isEqualTo("orders-api");
        assertThat(info.contextPath()).isEqualTo("/shop");
        assertThat(info.buildTool()).isEqualTo("maven");
    }

    @Test
    void resolvesPropertyPlaceholdersAndBootBom() throws IOException {
        write("pom.xml", """
                <project>
                  <artifactId>billing</artifactId>
                  <version>${revision}</version>
                  <properties><revision>2.0.0</revision><maven.compiler.release>17</maven.compiler.release></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.5.0</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """);
        write("src/main/resources/application.properties", "server.servlet.context-path=/\n");

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.name()).isEqualTo("billing");
        assertThat(info.version()).isEqualTo("2.0.0");
        assertThat(info.javaVersion()).isEqualTo("17");
        assertThat(info.springBootVersion()).isEqualTo("3.5.0");
        assertThat(info.contextPath()).isEmpty();
    }

    @Test
    void readsGradleKotlinProject() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"inventory\"\n");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("org.springframework.boot") version "3.3.2"
                }
                version = "0.9.0"
                description = "Inventory service"
                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    runtimeOnly("org.postgresql:postgresql")
                    implementation("com.google.guava:guava:33.0.0-jre")
                    testImplementation("org.springframework.boot:spring-boot-starter-test")
                }
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.name()).isEqualTo("inventory");
        assertThat(info.version()).isEqualTo("0.9.0");
        assertThat(info.description()).isEqualTo("Inventory service");
        assertThat(info.javaVersion()).isEqualTo("21");
        assertThat(info.springBootVersion()).isEqualTo("3.3.2");
        assertThat(info.dependencies()).containsExactly("postgresql", "spring-boot-starter-web");
        assertThat(info.buildTool()).isEqualTo("gradle");
    }

    @Test
    void skipsGradleTestConfigurationsInGroovyDsl() throws IOException {
        write("build.gradle", """
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    compileOnly 'org.projectlombok:lombok'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                    testRuntimeOnly 'com.h2database:h2'
                    testCompileOnly 'org.projectlombok:lombok'
                    testAnnotationProcessor 'org.projectlombok:lombok'
                }
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.dependencies()).containsExactly("lombok", "spring-boot-starter-web");
        assertThat(info.buildTool()).isEqualTo("gradle");
    }

    @Test
    void fallsBackToTheDirectoryNameWithoutBuildFile() {
        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.name()).isEqualTo(dir.getFileName().toString());
        assertThat(info.buildTool()).isEqualTo("unknown");
        assertThat(info.dependencies()).isEmpty();
    }

    @Test
    void treatsNullYamlValuesAsUnset() throws IOException {
        write("src/main/resources/application.yml", """
                spring:
                  application:
                    name:
                server:
                  servlet:
                    context-path: ~
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.applicationName()).isEmpty();
        assertThat(info.contextPath()).isEmpty();
    }

    @Test
    void resolvesPlaceholdersToTheirDefaultValue() throws IOException {
        write("src/main/resources/application.yml", """
                spring:
                  application:
                    name: ${APP_NAME:orders-api}
                server:
                  servlet:
                    context-path: ${CTX:/api}
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.applicationName()).isEqualTo("orders-api");
        assertThat(info.contextPath()).isEqualTo("/api");
    }

    @Test
    void treatsPlaceholdersWithoutDefaultAsUnset() throws IOException {
        write("src/main/resources/application.yml", """
                spring:
                  application:
                    name: ${APP_NAME}
                server:
                  servlet:
                    context-path: ${CTX}
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.applicationName()).isEmpty();
        assertThat(info.contextPath()).isEmpty();
    }

    @Test
    void resolvesNestedPlaceholdersInPropertiesFiles() throws IOException {
        write("src/main/resources/application.properties", """
                spring.application.name=${APP_NAME:${FALLBACK_NAME:billing}}
                server.servlet.context-path=${CTX:${FALLBACK_CTX}}
                """);

        ProjectInfo info = new ProjectInfoReader().read(dir);

        assertThat(info.applicationName()).isEqualTo("billing");
        assertThat(info.contextPath()).isEmpty();
    }

    @Test
    void readsTheSampleApi() {
        ProjectInfo info = new ProjectInfoReader().read(Path.of("../examples/sample-api"));

        assertThat(info.name()).isEqualTo("Shop API");
        assertThat(info.springBootVersion()).isEqualTo("4.1.1");
        assertThat(info.javaVersion()).isEqualTo("21");
        assertThat(info.applicationName()).isEqualTo("shop-api");
        assertThat(info.contextPath()).isEqualTo("/shop");
        assertThat(info.dependencies()).containsExactly("h2", "spring-boot-starter-data-jpa",
                "spring-boot-starter-security", "spring-boot-starter-validation", "spring-boot-starter-webmvc");
    }
}
