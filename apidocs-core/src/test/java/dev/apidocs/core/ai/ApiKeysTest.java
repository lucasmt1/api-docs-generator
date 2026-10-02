package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"sk-SECRET\n", "sk-SECRET\r\n", "sk-SECRET\r", "sk-SE\u0000CRET", "sk-SECRET’", "sk-SECRET\u007f"})
    void rejectsKeysWithControlOrNonAsciiCharactersWithoutEchoingThem(String apiKey) {
        assertThatThrownBy(() -> ApiKeys.requireHeaderSafe(apiKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("sk-SE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sk-abc_DEF.123~-+/=", " sk-with-space", "", " ", "\n"})
    void acceptsOrdinaryAndBlankKeys(String apiKey) {
        assertThatCode(() -> ApiKeys.requireHeaderSafe(apiKey)).doesNotThrowAnyException();
    }

    @Test
    void acceptsANullKey() {
        assertThatCode(() -> ApiKeys.requireHeaderSafe(null)).doesNotThrowAnyException();
    }

    // --- redact: server and SDK texts made safe to show ---

    private static final String KEY = "my-Gateway.Key/0123+456=789";

    @Test
    void redactsTheExactKey() {
        assertThat(ApiKeys.redact("invalid key " + KEY + " given", KEY)).isEqualTo("invalid key *** given");
    }

    @Test
    void redactsTheUrlEncodedKey() {
        assertThat(ApiKeys.redact("GET /v1?key=my-Gateway.Key%2F0123%2B456%3D789 denied", KEY))
                .isEqualTo("GET /v1?key=*** denied");
    }

    @Test
    void redactsTheJsonEscapedKey() {
        String quoted = "ab\"cd\\ef/gh-0123456789";

        assertThat(ApiKeys.redact("{\"key\":\"ab\\\"cd\\\\ef/gh-0123456789\"}", quoted)).isEqualTo("{\"key\":\"***\"}");
        assertThat(ApiKeys.redact("{\"key\":\"ab\\\"cd\\\\ef\\/gh-0123456789\"}", quoted)).isEqualTo("{\"key\":\"***\"}");
    }

    @Test
    void redactsTheHeadAndTailOfALongKeyQuotedInPart() {
        assertThat(ApiKeys.redact("key my-Gateway.K... was revoked; key ending +456=789 too", KEY))
                .isEqualTo("key ***... was revoked; key ending *** too");
    }

    @Test
    void masksAKnownFormatKeyEchoedInPartWithoutLeavingAnyOfItsSecretCharacters() {
        String key = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";
        String gatewayKey = "sk-ant-api03-ZyXwVuTsRqPoNmLkJiHgFeDcBa9876543210";

        assertThat(ApiKeys.redact("key " + key.substring(0, 24) + "... is invalid", key))
                .isEqualTo("key sk-***... is invalid");
        assertThat(ApiKeys.redact("upstream key " + gatewayKey + " is invalid", key))
                .isEqualTo("upstream key sk-*** is invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "EMPTY", "ollama", "1234567"})
    void leavesTextAloneForDummyKeysTooShortToBeSecrets(String dummy) {
        String text = "x said EMPTY: the ollama server 1234567 rejected the request";

        assertThat(ApiKeys.redact(text, dummy)).isEqualTo(text);
    }

    @Test
    void masksKeysFromEightCharactersOn() {
        assertThat(ApiKeys.redact("key 12345678 and abcd%2Fefg rejected", "12345678"))
                .isEqualTo("key *** and abcd%2Fefg rejected");
        assertThat(ApiKeys.redact("key abcd%2Fefg rejected", "abcd/efg")).isEqualTo("key *** rejected");
    }

    @Test
    void doesNotRedactPartsOfAShortKey() {
        String shortKey = "abcdefgh12345";

        assertThat(ApiKeys.redact("abcdefgh1234 and 12345 but " + shortKey, shortKey))
                .isEqualTo("abcdefgh1234 and 12345 but ***");
    }

    @Test
    void redactsBearerTokensAndKnownKeyFormatsEvenWithoutAKey() {
        String text = "Authorization: Bearer other-token-123 refused; also sk-proj-AbCdEfGhIjKlMnOpQrSt";

        assertThat(ApiKeys.redact(text, null)).isEqualTo("Authorization: Bearer *** refused; also sk-***");
        assertThat(ApiKeys.redact(text, " ")).isEqualTo("Authorization: Bearer *** refused; also sk-***");
    }

    @Test
    void flattensWhitespaceAndShortensLongTexts() {
        assertThat(ApiKeys.redact("  line one\n\tline   two\r\n", KEY)).isEqualTo("line one line two");
        assertThat(ApiKeys.redact("x".repeat(1_000), KEY)).isEqualTo("x".repeat(300) + "...");
        assertThat(ApiKeys.redact(null, KEY)).isEmpty();
    }

    @Test
    void redactsBeforeShortening() {
        String text = "y".repeat(295) + KEY;

        assertThat(ApiKeys.redact(text, KEY)).isEqualTo("y".repeat(295) + "***");
    }
}
