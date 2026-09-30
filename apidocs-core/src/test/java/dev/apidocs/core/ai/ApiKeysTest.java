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
}
