package dev.apidocs.core.ai;

/** Shared checks on API keys before an adapter puts them into an HTTP header. */
final class ApiKeys {

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
}
