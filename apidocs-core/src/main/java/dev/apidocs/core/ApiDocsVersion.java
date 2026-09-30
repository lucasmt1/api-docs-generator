package dev.apidocs.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Version of the tool, filtered into a resource by Maven at build time. */
public final class ApiDocsVersion {

    private static final String VERSION = load();

    private ApiDocsVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = ApiDocsVersion.class.getResourceAsStream("/apidocs-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
