package dev.apidocs.cli;

import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.ai.LlmClientFactory;
import dev.apidocs.core.pipeline.DocumentationPipeline;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Supplier;

/** Everything a command needs from the outside world; replaced by fakes in tests. */
record CliContext(
        Map<String, String> environment,
        PrintWriter out,
        PrintWriter err,
        LlmClientFactory llmFactory,
        Supplier<DocumentationPipeline> pipeline,
        CancellationToken cancellation) {

    static CliContext system(CancellationToken cancellation) {
        Charset charset = consoleCharset();
        return new CliContext(System.getenv(),
                new PrintWriter(new OutputStreamWriter(System.out, charset), true),
                new PrintWriter(new OutputStreamWriter(System.err, charset), true),
                new LlmClientFactory(), DocumentationPipeline::new, cancellation);
    }

    static Charset consoleCharset() {
        String name = System.getProperty("stdout.encoding");
        try {
            return name == null ? Charset.defaultCharset() : Charset.forName(name);
        } catch (IllegalArgumentException unknown) {
            return Charset.defaultCharset();
        }
    }
}
