package dev.apidocs.cli;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.ApiDocsException;
import dev.apidocs.core.ApiDocsVersion;
import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.ConfigException;
import dev.apidocs.core.ai.LlmException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

@Command(name = "apidocs", mixinStandardHelpOptions = true, versionProvider = ApiDocsCli.VersionProvider.class,
        description = "Generates technical documentation, API reference and architectural overview for Spring Boot APIs.")
public final class ApiDocsCli implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        throw new ParameterException(spec.commandLine(), "Missing command: use 'generate' or 'analyze'.");
    }

    public static void main(String[] args) {
        CancellationToken cancellation = new CancellationToken();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean done = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (done.get()) {
                return;
            }
            cancellation.cancel();
            try {
                finished.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(ExitCodes.CANCELLED);
        }, "apidocs-cancel"));
        int exitCode = ExitCodes.USAGE;
        try {
            exitCode = execute(args, CliContext.system(cancellation));
        } finally {
            // Whatever happens, the shutdown hook must see that the run is over; otherwise it would wait for the
            // full timeout and then report a failure as a cancellation.
            done.set(true);
            finished.countDown();
        }
        System.exit(exitCode);
    }

    public static int execute(String[] args, CliContext context) {
        CommandLine commandLine = new CommandLine(new ApiDocsCli())
                .addSubcommand("generate", new GenerateCommand(context))
                .addSubcommand("analyze", new AnalyzeCommand(context));
        commandLine.setOut(context.out());
        commandLine.setErr(context.err());
        commandLine.setParameterExceptionHandler((ex, arguments) -> {
            PrintWriter err = ex.getCommandLine().getErr();
            err.println("Error: " + ex.getMessage());
            ex.getCommandLine().usage(err);
            return ExitCodes.USAGE;
        });
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) ->
                handle(ex, cmd.getErr(), isVerbose(parseResult)));
        try {
            return commandLine.execute(args);
        } catch (Throwable error) {
            // picocli hands only Exceptions to the handler above; a JVM Error (a StackOverflowError from deeply
            // nested untrusted sources, an OutOfMemoryError) escapes execute and must still end as a clean failure.
            return handle(error, context.err(), isVerbose(commandLine.getParseResult()));
        }
    }

    static int handle(Throwable exception, PrintWriter err, boolean verbose) {
        int code;
        String message;
        if (exception instanceof CancelledException) {
            code = ExitCodes.CANCELLED;
            message = "Cancelled.";
        } else if (exception instanceof ConfigException) {
            code = ExitCodes.USAGE;
            message = "Error: " + exception.getMessage();
        } else if (exception instanceof AnalysisException) {
            code = ExitCodes.ANALYSIS;
            message = "Error: " + exception.getMessage();
        } else if (exception instanceof LlmException) {
            code = ExitCodes.LLM;
            message = "Error: " + exception.getMessage();
        } else if (exception instanceof ApiDocsException) {
            code = ExitCodes.USAGE;
            message = "Error: " + exception.getMessage();
        } else {
            code = ExitCodes.USAGE;
            message = "Unexpected error: " + exception;
        }
        err.println(message);
        if (verbose) {
            exception.printStackTrace(err);
        }
        err.flush();
        return code;
    }

    private static boolean isVerbose(ParseResult parseResult) {
        ParseResult subcommand = parseResult == null ? null : parseResult.subcommand();
        return subcommand != null && subcommand.hasMatchedOption("--verbose");
    }

    static final class VersionProvider implements IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[] {"apidocs " + ApiDocsVersion.get()};
        }
    }
}
