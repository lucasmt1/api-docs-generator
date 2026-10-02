package dev.apidocs.core.ai;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import dev.apidocs.core.support.Secrets;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Shared handling of API keys: checks before an adapter puts one into a header, masking in text shown to users. */
final class ApiKeys {

    /** Longest provider text kept in an error detail. */
    static final int MAX_TEXT = 300;
    /** Shorter keys are dummies (keyless local servers), not secrets, so they are not masked in text. */
    private static final int MASKED_KEY_MIN_LENGTH = 8;
    /** Keys at least this long also have their head and tail masked when quoted in part. */
    private static final int PARTIAL_MASK_MIN_LENGTH = 16;
    private static final int HEAD = 12;
    private static final int TAIL = 8;

    private ApiKeys() {
    }

    /**
     * A key that cannot be an HTTP header value (stray CR/LF from a file, ...) is a configuration error. A null or
     * blank key is allowed: the adapters send no credentials then.
     */
    static void requireHeaderSafe(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        boolean safe = apiKey.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
        if (!safe) {
            // never include the value: it is a secret
            throw new IllegalArgumentException(
                    "API key contains control or non-ASCII characters; check the environment variable it is read from");
        }
    }

    /**
     * Text from a provider or its SDK made safe to show: the key masked in every form a server or gateway may echo
     * it (as is, URL-encoded, JSON-escaped), then bearer tokens and secrets of known formats, then whatever is left of
     * the key's head or tail; finally flattened to one line and shortened. A null, blank or short key (a dummy such
     * as {@code EMPTY} or {@code ollama} for keyless servers) masks only the generic secrets: masking it everywhere
     * would garble the text without protecting anything.
     */
    static String redact(String text, String apiKey) {
        boolean keyed = apiKey != null && !apiKey.isBlank() && apiKey.length() >= MASKED_KEY_MIN_LENGTH;
        String masked = text == null ? "" : text;
        if (keyed) {
            for (String form : forms(apiKey)) {
                masked = masked.replace(form, Secrets.MASK);
            }
        }
        // Known formats before the head and tail: the head of a vendor key is often its public prefix
        // (sk-ant-api03), and masking it first would hide the prefix the format needs to recognize the rest.
        masked = Secrets.maskKnownSecrets(masked);
        if (keyed && apiKey.length() >= PARTIAL_MASK_MIN_LENGTH) {
            masked = masked.replace(apiKey.substring(0, HEAD), Secrets.MASK)
                    .replace(apiKey.substring(apiKey.length() - TAIL), Secrets.MASK);
        }
        // masked before shortening: a cut must never leave part of a secret behind
        String flat = masked.replaceAll("\\s+", " ").strip();
        return flat.length() > MAX_TEXT ? flat.substring(0, MAX_TEXT) + "..." : flat;
    }

    /** The encodings of the key a server may echo, longest first so no form is cut by a shorter one. */
    private static List<String> forms(String apiKey) {
        String json = new String(JsonStringEncoder.getInstance().quoteAsString(apiKey));
        return Stream.of(apiKey, URLEncoder.encode(apiKey, StandardCharsets.UTF_8), json, json.replace("/", "\\/"))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}
