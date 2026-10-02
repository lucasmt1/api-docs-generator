package dev.apidocs.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "sk-proj-AbCdEfGhIjKlMnOpQrSt",
            "sk-ant-api03-AbCdEfGhIjKlMnOp",
            "sk-AbCdEfGhIjKlMnOp1234",
            "AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r",
            "ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789",
            "gho_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789",
            "github_pat_11ABCDEFG0123456789_abcdefghij",
            "AKIAIOSFODNN7EXAMPLE",
            "xoxb-1234567890-abcdefghij",
            "gsk_AbCdEfGhIjKlMnOpQrStUv01",
            "hf_AbCdEfGhIjKlMnOpQrStUv01",
            "xai-AbCdEfGhIjKlMnOpQrStUv01",
            "pplx-AbCdEfGhIjKlMnOpQrStUv01",
            "-----BEGIN RSA PRIVATE KEY-----",
            "-----BEGIN PRIVATE KEY-----",
            "use the key sk-proj-AbCdEfGhIjKlMnOpQrSt for this"})
    void recognizesKnownSecretFormatsAnywhereInAText(String text) {
        assertThat(Secrets.containsKnownSecret(text)).isTrue();
        assertThat(Secrets.looksLikeKey(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "Stock keeping unit", "GEMINI_API_KEY", "APIDOCS_PROXY_KEY", "**/legacy/**",
            "risk-assessment-guidelines-v2", "**/task-management-service/**", "sk-short", "AKIA-not-a-key",
            "-----BEGIN PUBLIC KEY-----", "maxai-AbCdEfGhIjKlMnOpQrStUv01", "pdf_hf_short", "tasks_gsk_tooShort"})
    void ignoresOrdinaryTexts(String text) {
        assertThat(Secrets.containsKnownSecret(text)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s, true",
            "abcd1234efgh5678ijkl, true",
            "abcd1234efgh5678ijk, false",
            "ABCD1234EFGH5678IJKL9012, false",
            "abcdefghijklmnopqrstuvwxyz, false",
            "GEMINI_API_KEY, false",
            "my_openai_key, false"})
    void looksLikeAKeyWhenItHasAKnownFormatOrIsLongAndMixesLowercaseAndDigits(String value, boolean expected) {
        assertThat(Secrets.looksLikeKey(value)).isEqualTo(expected);
    }

    @Test
    void nullIsNeverASecret() {
        assertThat(Secrets.containsKnownSecret(null)).isFalse();
        assertThat(Secrets.looksLikeKey(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            GEMINI_API_KEY                            | true
            _private                                  | true
            APIDOCS_PROXY_KEY                         | true
            AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s | false
            sk-proj-AbC123/xyz+9                      | false
            1ST_KEY                                   | false
            my key                                    | false
            ''                                        | false
            """)
    void showsOnlyValidVariableNamesThatDoNotLookLikeKeys(String name, boolean expected) {
        assertThat(Secrets.isShowableVariableName(name)).isEqualTo(expected);
    }

    @Test
    void masksBearerTokensAndKnownFormatsKeepingTheirPrefix() {
        String text = "Authorization: Bearer abc.DEF_123-xyz~+/= rejected; keys sk-proj-AbCdEfGhIjKlMnOpQrSt, "
                + "AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r, ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789, "
                + "github_pat_11ABCDEFG0123456789_abcdefghij, AKIAIOSFODNN7EXAMPLE, xoxb-1234567890-abcdefghij";

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo("Authorization: Bearer *** rejected; keys sk-***, "
                + "AIza***, ghp_***, github_pat_***, AKIA***, xoxb-***");
    }

    @Test
    void masksGroqHuggingFaceXaiAndPerplexityKeys() {
        String text = "keys gsk_AbCdEfGhIjKlMnOpQrStUv01, hf_AbCdEfGhIjKlMnOpQrStUv01, xai-AbCdEfGhIjKlMnOpQrStUv01 "
                + "and pplx-AbCdEfGhIjKlMnOpQrStUv01 rejected";

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo("keys gsk_***, hf_***, xai-*** and pplx-*** rejected");
    }

    @Test
    void masksAPrivateKeyBlockUpToItsEndMarkerOrTheEndOfTheText() {
        String block = "before -----BEGIN RSA PRIVATE KEY-----\nMIIEow\nIBAAKC\n-----END RSA PRIVATE KEY----- after";
        String cut = "x -----BEGIN PRIVATE KEY-----\nMIIEow\nIBAAKC";

        assertThat(Secrets.maskKnownSecrets(block)).isEqualTo("before -----BEGIN RSA PRIVATE KEY-----*** after");
        assertThat(Secrets.maskKnownSecrets(cut)).isEqualTo("x -----BEGIN PRIVATE KEY-----***");
    }

    @Test
    void masksAndRecognizesSecretsRightAfterAJsonEscape() {
        String body = "{\"error\":\"bad keys:\\nsk-proj-AbCdEfGhIjKlMnOpQrSt\\tAIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r"
                + "\\\"ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789\\u0022xoxb-1234567890-abcdefghij\\r\\nAKIAIOSFODNN7EXAMPLE\"}";

        assertThat(Secrets.maskKnownSecrets(body)).isEqualTo(
                "{\"error\":\"bad keys:\\nsk-***\\tAIza***\\\"ghp_***\\u0022xoxb-***\\r\\nAKIA***\"}");
        assertThat(Secrets.containsKnownSecret("note:\\nsk-proj-AbCdEfGhIjKlMnOpQrSt")).isTrue();
    }

    @Test
    void masksBearerTokensWhateverTheCaseOfTheScheme() {
        assertThat(Secrets.maskKnownSecrets("authorization: bearer abc123")).isEqualTo("authorization: bearer ***");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String text = "Quota exceeded for risk-assessment-guidelines in project task-scheduler-production-eu";

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo(text);
        assertThat(Secrets.maskKnownSecrets(null)).isEmpty();
    }
}
