package dev.apidocs.cli;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** The core logs through System.Logger (backed by java.util.logging); --verbose shows debug messages. */
final class Logging {

    /** Held strongly: java.util.logging keeps loggers weakly, and a collected logger would lose its level. */
    private static final Logger APPLICATION = Logger.getLogger("dev.apidocs");

    private Logging() {
    }

    static void configure(boolean verbose) {
        Logger root = LogManager.getLogManager().getLogger("");
        Level handlerLevel = verbose ? Level.FINE : Level.WARNING;
        // Only the tool's own loggers go to FINE: the JDK logs at FINE too (HTTP client internals, every trusted
        // X.509 certificate when TLS starts), which would drown the messages the user asked for.
        root.setLevel(Level.WARNING);
        APPLICATION.setLevel(verbose ? Level.FINE : null);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(handlerLevel);
        }
    }
}
