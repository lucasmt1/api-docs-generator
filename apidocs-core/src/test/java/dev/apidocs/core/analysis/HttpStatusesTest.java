package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class HttpStatusesTest {

    @Test
    void mapsConstantsLiteralsAndValueOf() {
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("HttpStatus.CREATED"))).hasValue(201);
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("UNPROCESSABLE_ENTITY"))).hasValue(422);
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("422"))).hasValue(422);
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("HttpStatus.valueOf(404)"))).hasValue(404);
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("SOMETHING_ELSE"))).isEmpty();
    }

    @Test
    void ignoresIntegerLiteralsOutsideTheIntRange() {
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("99999999999"))).isEmpty();
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("HttpStatus.valueOf(99999999999)"))).isEmpty();
        assertThat(HttpStatuses.code(StaticJavaParser.parseExpression("2_01"))).hasValue(201);
    }

    @Test
    void providesReasonPhrases() {
        assertThat(HttpStatuses.reason(404)).isEqualTo("Not Found");
        assertThat(HttpStatuses.reason(299)).isEqualTo("HTTP 299");
    }

    @Test
    void providesReasonPhrasesForRedirectAndClientErrorCodes() {
        assertThat(HttpStatuses.reason(301)).isEqualTo("Moved Permanently");
        assertThat(HttpStatuses.reason(302)).isEqualTo("Found");
        assertThat(HttpStatuses.reason(304)).isEqualTo("Not Modified");
        assertThat(HttpStatuses.reason(402)).isEqualTo("Payment Required");
        assertThat(HttpStatuses.reason(406)).isEqualTo("Not Acceptable");
        assertThat(HttpStatuses.reason(423)).isEqualTo("Locked");
    }
}
