package dev.apidocs.cli;

import dev.apidocs.core.pipeline.ProgressEvent;
import dev.apidocs.core.pipeline.ProgressListener;
import dev.apidocs.core.pipeline.Stage;
import java.io.PrintWriter;
import java.util.stream.Collectors;

/** Prints one line per pipeline event, mirroring the stages of the reference diagram. */
final class ConsoleProgress implements ProgressListener {

    private final PrintWriter out;
    private final boolean unicode;

    ConsoleProgress(PrintWriter out) {
        this(out, CliContext.consoleCharset().newEncoder().canEncode("✔●·"));
    }

    ConsoleProgress(PrintWriter out, boolean unicode) {
        this.out = out;
        this.unicode = unicode;
    }

    @Override
    public void onEvent(ProgressEvent event) {
        StringBuilder line = new StringBuilder(symbol(event.stage())).append(' ').append(label(event.stage()));
        if (event.totalSteps() > 0) {
            line.append(" (").append(event.step()).append('/').append(event.totalSteps()).append(')');
        }
        if (!event.message().isBlank()) {
            line.append(": ").append(event.message());
        }
        if (!event.counters().isEmpty()) {
            line.append("  ").append(event.counters().entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey())
                    .collect(Collectors.joining(unicode ? " · " : ", ")));
        }
        out.println(line);
        out.flush();
    }

    private String symbol(Stage stage) {
        if (stage == Stage.AI_PROCESSING) {
            return unicode ? "●" : "*";
        }
        return unicode ? "✔" : "+";
    }

    static String label(Stage stage) {
        return switch (stage) {
            case SOURCE_LOADING -> "Sources";
            case PARSING -> "Static analysis";
            case EXTRACTION -> "Code extractor";
            case CONTEXT_BUILDING -> "Context";
            case AI_PROCESSING -> "AI processing";
            case RENDERING -> "Rendering";
            case DELIVERY -> "Delivered to";
        };
    }
}
