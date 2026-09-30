package dev.apidocs.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Shrinks service method bodies (longest first) until the rendered prompt fits the token budget. */
public final class ContextBudget {

    public static final String TRUNCATION_MARKER = "// ... (truncated by apidocs)";
    static final int MIN_BODY_CHARS = 200;

    public record Fitted(String text, boolean truncated, boolean overBudget) {
    }

    public Fitted fit(Function<List<ServiceMethodRef>, String> render, List<ServiceMethodRef> methods, int maxTokens) {
        List<ServiceMethodRef> current = new ArrayList<>(methods);
        String text = render.apply(current);
        boolean truncated = false;
        while (TokenEstimator.estimate(text) > maxTokens) {
            int longest = -1;
            for (int i = 0; i < current.size(); i++) {
                int length = current.get(i).method().body().length();
                if (length > MIN_BODY_CHARS && (longest < 0 || length > current.get(longest).method().body().length())) {
                    longest = i;
                }
            }
            if (longest < 0) {
                return new Fitted(text, truncated, true);
            }
            ServiceMethodRef reference = current.get(longest);
            String body = reference.method().body();
            String shortened = body.substring(0, body.length() / 2).stripTrailing() + "\n" + TRUNCATION_MARKER;
            current.set(longest, new ServiceMethodRef(reference.service(), reference.method().withBody(shortened)));
            truncated = true;
            text = render.apply(current);
        }
        return new Fitted(text, truncated, false);
    }
}
