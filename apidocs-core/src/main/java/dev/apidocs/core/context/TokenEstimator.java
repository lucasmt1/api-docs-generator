package dev.apidocs.core.context;

/** Provider-agnostic token estimate (about four characters per token). */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }
}
