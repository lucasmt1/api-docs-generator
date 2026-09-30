package dev.apidocs.core.analysis;

import static java.util.Map.entry;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.Map;
import java.util.OptionalInt;

public final class HttpStatuses {

    private static final Map<String, Integer> BY_NAME = Map.ofEntries(
            entry("OK", 200), entry("CREATED", 201), entry("ACCEPTED", 202), entry("NO_CONTENT", 204),
            entry("MOVED_PERMANENTLY", 301), entry("FOUND", 302), entry("NOT_MODIFIED", 304),
            entry("BAD_REQUEST", 400), entry("UNAUTHORIZED", 401), entry("PAYMENT_REQUIRED", 402),
            entry("FORBIDDEN", 403), entry("NOT_FOUND", 404), entry("METHOD_NOT_ALLOWED", 405),
            entry("NOT_ACCEPTABLE", 406), entry("CONFLICT", 409), entry("GONE", 410),
            entry("PRECONDITION_FAILED", 412), entry("PAYLOAD_TOO_LARGE", 413), entry("CONTENT_TOO_LARGE", 413),
            entry("UNSUPPORTED_MEDIA_TYPE", 415), entry("UNPROCESSABLE_ENTITY", 422),
            entry("UNPROCESSABLE_CONTENT", 422), entry("LOCKED", 423), entry("TOO_MANY_REQUESTS", 429),
            entry("INTERNAL_SERVER_ERROR", 500), entry("NOT_IMPLEMENTED", 501), entry("BAD_GATEWAY", 502),
            entry("SERVICE_UNAVAILABLE", 503), entry("GATEWAY_TIMEOUT", 504));

    private static final Map<Integer, String> REASONS = Map.ofEntries(
            entry(200, "OK"), entry(201, "Created"), entry(202, "Accepted"), entry(204, "No Content"),
            entry(301, "Moved Permanently"), entry(302, "Found"), entry(304, "Not Modified"),
            entry(400, "Bad Request"), entry(401, "Unauthorized"), entry(402, "Payment Required"),
            entry(403, "Forbidden"), entry(404, "Not Found"), entry(405, "Method Not Allowed"),
            entry(406, "Not Acceptable"), entry(409, "Conflict"), entry(410, "Gone"),
            entry(412, "Precondition Failed"), entry(413, "Content Too Large"), entry(415, "Unsupported Media Type"),
            entry(422, "Unprocessable Content"), entry(423, "Locked"), entry(429, "Too Many Requests"),
            entry(500, "Internal Server Error"), entry(501, "Not Implemented"), entry(502, "Bad Gateway"),
            entry(503, "Service Unavailable"), entry(504, "Gateway Timeout"));

    private HttpStatuses() {
    }

    /** Status code of {@code HttpStatus.X}, {@code X}, a literal int or {@code HttpStatus.valueOf(n)}. */
    public static OptionalInt code(Expression expression) {
        if (expression instanceof IntegerLiteralExpr literal) {
            return OptionalInt.of(literal.asNumber().intValue());
        }
        if (expression instanceof MethodCallExpr call && call.getNameAsString().equals("valueOf")
                && call.getArguments().size() == 1) {
            return code(call.getArgument(0));
        }
        Integer code = BY_NAME.get(Annotations.lastIdentifier(expression));
        return code == null ? OptionalInt.empty() : OptionalInt.of(code);
    }

    public static String reason(int status) {
        return REASONS.getOrDefault(status, "HTTP " + status);
    }
}
