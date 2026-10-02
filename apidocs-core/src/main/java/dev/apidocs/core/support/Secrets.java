package dev.apidocs.core.support;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Recognizes secrets (API keys, tokens, private keys) in free text: the one place that knows their formats. Used to
 * refuse them in committed configuration, to avoid echoing them in error messages and to mask them in text that
 * came from a provider.
 */
public final class Secrets {

    /** What a masked secret becomes. */
    public static final String MASK = "***";

    /**
     * Where a token may start: not inside a word, so that words such as {@code risk-assessment-guidelines} do not
     * look like an {@code sk-} key; but right after a JSON escape (a backslash and one of {@code nrtbf}, or a
     * four-digit unicode escape) it may, because error bodies quote keys inside JSON strings.
     */
    private static final String TOKEN_START = "(?:(?<![A-Za-z0-9])|(?<=\\\\[nrtbf])|(?<=\\\\u[0-9A-Fa-f]{4}))";
    /**
     * Known secret formats. Group {@code prefix} survives masking, so the kind of secret stays recognizable. A
     * private key is masked from its header to its end marker (or to the end of the text).
     */
    private static final List<Pattern> KNOWN_FORMATS = Stream.concat(
            Stream.of("(?<prefix>sk-)[A-Za-z0-9_-]{16,}",
                            "(?<prefix>AIza)[0-9A-Za-z_-]{35}",
                            "(?<prefix>gh[pousr]_)[A-Za-z0-9]{36,}",
                            "(?<prefix>github_pat_)[A-Za-z0-9_]{20,}",
                            "(?<prefix>AKIA)[0-9A-Z]{16}",
                            "(?<prefix>xox[abprs]-)[A-Za-z0-9-]{10,}",
                            "(?<prefix>gsk_)[A-Za-z0-9]{20,}",
                            "(?<prefix>hf_)[A-Za-z0-9]{20,}",
                            "(?<prefix>xai-)[A-Za-z0-9]{20,}",
                            "(?<prefix>pplx-)[A-Za-z0-9]{20,}")
                    .map(token -> TOKEN_START + token),
            Stream.of("(?<prefix>-----BEGIN [A-Z ]*PRIVATE KEY-----)"
                    + "(?:[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----|[\\s\\S]*)"))
            .map(Pattern::compile)
            .toList();
    /** An HTTP bearer credential (RFC 6750 token characters); the scheme is case-insensitive. */
    private static final Pattern BEARER = Pattern.compile("(?i)(?<prefix>\\bBearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MIN_KEY_LIKE_LENGTH = 20;

    private Secrets() {
    }

    /** True when the text holds a secret of a known format anywhere. */
    public static boolean containsKnownSecret(String text) {
        return text != null && KNOWN_FORMATS.stream().anyMatch(format -> format.matcher(text).find());
    }

    /**
     * True when a value that should be a name (of an environment variable, say) may be a key instead: it holds a
     * secret of a known format, or it is long and mixes lowercase letters and digits, as random tokens do.
     */
    public static boolean looksLikeKey(String value) {
        if (value == null) {
            return false;
        }
        return containsKnownSecret(value) || value.length() >= MIN_KEY_LIKE_LENGTH
                && value.chars().anyMatch(c -> c >= 'a' && c <= 'z')
                && value.chars().anyMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * True when an environment variable name may be quoted in a message: it is a valid name and does not look like a
     * key. Anything else may be a key pasted in place of the name, so it is never shown.
     */
    public static boolean isShowableVariableName(String name) {
        return name != null && VARIABLE_NAME.matcher(name).matches() && !looksLikeKey(name);
    }

    /** The text with bearer tokens and secrets of known formats replaced by their prefix and {@link #MASK}. */
    public static String maskKnownSecrets(String text) {
        if (text == null) {
            return "";
        }
        // bearer first: masking the token's own format first would leave its prefix behind the scheme
        String masked = mask(BEARER, text);
        for (Pattern format : KNOWN_FORMATS) {
            masked = mask(format, masked);
        }
        return masked;
    }

    private static String mask(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(match -> Matcher.quoteReplacement(match.group("prefix") + MASK));
    }
}
