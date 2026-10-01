package dev.apidocs.cli;

import dev.apidocs.core.config.ConfigOverrides;
import dev.apidocs.core.config.ConfigResolver;
import dev.apidocs.core.config.GeneratorConfig;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.pipeline.ProgressListener;
import dev.apidocs.core.support.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "analyze", mixinStandardHelpOptions = true, versionProvider = ApiDocsCli.VersionProvider.class,
        description = "Run only the static analysis and print the extracted model as JSON (no LLM).")
final class AnalyzeCommand implements Callable<Integer> {

    private final CliContext context;

    @Parameters(index = "0", paramLabel = "<project-dir>", description = "Root directory of the Spring Boot project.")
    Path projectDir;

    @Option(names = {"-o", "--output"}, description = "Write the model to this file instead of standard output.")
    Path output;

    @Option(names = "--config", description = "Path to a .apidocs.yml file (default: <project-dir>/.apidocs.yml).")
    Path config;

    @Option(names = {"-v", "--verbose"}, description = "Show debug logs and stack traces.")
    boolean verbose;

    AnalyzeCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() throws IOException {
        Logging.configure(verbose);
        GeneratorConfig resolved = new ConfigResolver().resolve(new ConfigOverrides(projectDir, null, config, "dry-run",
                null, null, null, null, null, true, false), context.environment());
        ApiModel model = context.pipeline().get().analyze(resolved, ProgressListener.NONE, context.cancellation());
        String json = JsonSupport.toPrettyJson(model);
        if (output == null) {
            context.out().print(json);
            context.out().flush();
        } else {
            Path target = output.toAbsolutePath().normalize();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, json);
            context.err().println("Model written to " + target);
        }
        return ExitCodes.OK;
    }
}
