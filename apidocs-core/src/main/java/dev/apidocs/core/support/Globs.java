package dev.apidocs.core.support;

import java.util.regex.Pattern;

/** Minimal, platform-independent glob matching for '/'-separated relative paths. */
public final class Globs {

    private static final String REGEX_SPECIALS = "\\.[]{}()+-^$|";

    private Globs() {
    }

    public static Pattern toPattern(String glob) {
        String normalized = glob.replace('\\', '/');
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < normalized.length() && normalized.charAt(i + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = i + 2 < normalized.length() && normalized.charAt(i + 2) == '/';
                    regex.append(followedBySlash ? "(?:.*/)?" : ".*");
                    i += followedBySlash ? 2 : 1;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                if (REGEX_SPECIALS.indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return Pattern.compile(regex.toString());
    }
}
