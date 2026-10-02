package dev.apidocs.core.support;

import static dev.apidocs.core.testsupport.FakeSecrets.ANTHROPIC_SHORT_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.AWS_ACCESS_KEY_ID;
import static dev.apidocs.core.testsupport.FakeSecrets.GITHUB_FINE_GRAINED_TOKEN;
import static dev.apidocs.core.testsupport.FakeSecrets.GITHUB_OAUTH_TOKEN;
import static dev.apidocs.core.testsupport.FakeSecrets.GITHUB_TOKEN;
import static dev.apidocs.core.testsupport.FakeSecrets.GOOGLE_API_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.GOOGLE_API_KEY_LONG;
import static dev.apidocs.core.testsupport.FakeSecrets.GROQ_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.HUGGING_FACE_TOKEN;
import static dev.apidocs.core.testsupport.FakeSecrets.OPENAI_LEGACY_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.OPENAI_PROJECT_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.PERPLEXITY_KEY;
import static dev.apidocs.core.testsupport.FakeSecrets.PRIVATE_KEY_HEADER;
import static dev.apidocs.core.testsupport.FakeSecrets.RISK_ASSESSMENT_GUIDELINES;
import static dev.apidocs.core.testsupport.FakeSecrets.RSA_PRIVATE_KEY_HEADER;
import static dev.apidocs.core.testsupport.FakeSecrets.SLACK_TOKEN;
import static dev.apidocs.core.testsupport.FakeSecrets.TASK_MANAGEMENT_GLOB;
import static dev.apidocs.core.testsupport.FakeSecrets.TASK_SCHEDULER_PRODUCTION_EU;
import static dev.apidocs.core.testsupport.FakeSecrets.XAI_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            OPENAI_PROJECT_KEY,
            ANTHROPIC_SHORT_KEY,
            OPENAI_LEGACY_KEY,
            GOOGLE_API_KEY,
            GITHUB_TOKEN,
            GITHUB_OAUTH_TOKEN,
            GITHUB_FINE_GRAINED_TOKEN,
            AWS_ACCESS_KEY_ID,
            SLACK_TOKEN,
            GROQ_KEY,
            HUGGING_FACE_TOKEN,
            XAI_KEY,
            PERPLEXITY_KEY,
            RSA_PRIVATE_KEY_HEADER,
            PRIVATE_KEY_HEADER,
            "use the key " + OPENAI_PROJECT_KEY + " for this"})
    void recognizesKnownSecretFormatsAnywhereInAText(String text) {
        assertThat(Secrets.containsKnownSecret(text)).isTrue();
        assertThat(Secrets.looksLikeKey(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "Stock keeping unit", "GEMINI_API_KEY", "APIDOCS_PROXY_KEY", "**/legacy/**",
            RISK_ASSESSMENT_GUIDELINES + "-v2", TASK_MANAGEMENT_GLOB, "sk-short", "AKIA-not-a-key",
            "-----BEGIN PUBLIC KEY-----", "ma" + XAI_KEY, "pdf_hf_short", "tasks_gsk_tooShort"})
    void ignoresOrdinaryTexts(String text) {
        assertThat(Secrets.containsKnownSecret(text)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            GOOGLE_API_KEY_LONG + ", true",
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
    @CsvSource(delimiter = '|', value = {
            "GEMINI_API_KEY                            | true",
            "_private                                  | true",
            "APIDOCS_PROXY_KEY                         | true",
            GOOGLE_API_KEY_LONG + " | false",
            "sk-proj-AbC123/xyz+9                      | false",
            "1ST_KEY                                   | false",
            "my key                                    | false",
            "''                                        | false"})
    void showsOnlyValidVariableNamesThatDoNotLookLikeKeys(String name, boolean expected) {
        assertThat(Secrets.isShowableVariableName(name)).isEqualTo(expected);
    }

    @Test
    void masksBearerTokensAndKnownFormatsKeepingTheirPrefix() {
        String text = "Authorization: Bearer abc.DEF_123-xyz~+/= rejected; keys " + OPENAI_PROJECT_KEY + ", "
                + GOOGLE_API_KEY + ", " + GITHUB_TOKEN + ", " + GITHUB_FINE_GRAINED_TOKEN + ", "
                + AWS_ACCESS_KEY_ID + ", " + SLACK_TOKEN;

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo("Authorization: Bearer *** rejected; keys sk-***, "
                + "AIza***, ghp_***, github_pat_***, AKIA***, xoxb-***");
    }

    @Test
    void masksGroqHuggingFaceXaiAndPerplexityKeys() {
        String text = "keys " + GROQ_KEY + ", " + HUGGING_FACE_TOKEN + ", " + XAI_KEY + " and " + PERPLEXITY_KEY
                + " rejected";

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo("keys gsk_***, hf_***, xai-*** and pplx-*** rejected");
    }

    @Test
    void masksAPrivateKeyBlockUpToItsEndMarkerOrTheEndOfTheText() {
        String block = "before " + RSA_PRIVATE_KEY_HEADER + "\nMIIEow\nIBAAKC\n-----END RSA PRIVATE KEY----- after";
        String cut = "x " + PRIVATE_KEY_HEADER + "\nMIIEow\nIBAAKC";

        assertThat(Secrets.maskKnownSecrets(block)).isEqualTo("before " + RSA_PRIVATE_KEY_HEADER + "*** after");
        assertThat(Secrets.maskKnownSecrets(cut)).isEqualTo("x " + PRIVATE_KEY_HEADER + "***");
    }

    @Test
    void masksAndRecognizesSecretsRightAfterAJsonEscape() {
        String body = "{\"error\":\"bad keys:\\n" + OPENAI_PROJECT_KEY + "\\t" + GOOGLE_API_KEY
                + "\\\"" + GITHUB_TOKEN + "\\u0022" + SLACK_TOKEN + "\\r\\n" + AWS_ACCESS_KEY_ID + "\"}";

        assertThat(Secrets.maskKnownSecrets(body)).isEqualTo(
                "{\"error\":\"bad keys:\\nsk-***\\tAIza***\\\"ghp_***\\u0022xoxb-***\\r\\nAKIA***\"}");
        assertThat(Secrets.containsKnownSecret("note:\\n" + OPENAI_PROJECT_KEY)).isTrue();
    }

    @Test
    void masksBearerTokensWhateverTheCaseOfTheScheme() {
        assertThat(Secrets.maskKnownSecrets("authorization: bearer abc123")).isEqualTo("authorization: bearer ***");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String text = "Quota exceeded for " + RISK_ASSESSMENT_GUIDELINES + " in project "
                + TASK_SCHEDULER_PRODUCTION_EU;

        assertThat(Secrets.maskKnownSecrets(text)).isEqualTo(text);
        assertThat(Secrets.maskKnownSecrets(null)).isEmpty();
    }
}
