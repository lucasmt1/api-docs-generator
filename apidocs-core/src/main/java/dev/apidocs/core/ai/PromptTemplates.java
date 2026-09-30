package dev.apidocs.core.ai;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The Instruction Set: prompt templates under {@code /prompts/*.mustache}. */
public final class PromptTemplates {

    private final Mustache.Compiler compiler = Mustache.compiler()
            .escapeHTML(false)
            .defaultValue("")
            .emptyStringIsFalse(true);
    private final Map<String, Template> templates = new ConcurrentHashMap<>();

    public String render(String name, Map<String, Object> context) {
        return templates.computeIfAbsent(name, this::load).execute(context).replace("\r\n", "\n").strip();
    }

    private Template load(String name) {
        String path = "/prompts/" + name + ".mustache";
        try (InputStream in = PromptTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt template " + path);
            }
            return compiler.compile(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
