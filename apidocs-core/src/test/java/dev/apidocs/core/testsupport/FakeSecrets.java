package dev.apidocs.core.testsupport;

/** Fake secrets (and key-like text) for tests, split so that secret scanners do not flag the sources. */
public final class FakeSecrets {

    public static final String OPENAI_PROJECT_KEY = "sk-proj-" + "AbCdEfGhIjKlMnOpQrSt";
    public static final String OPENAI_LEGACY_KEY = "sk-" + "AbCdEfGhIjKlMnOp1234";
    public static final String ANTHROPIC_KEY = "sk-ant-" + "api03-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";
    public static final String ANTHROPIC_OTHER_KEY = "sk-ant-" + "api03-ZyXwVuTsRqPoNmLkJiHgFeDcBa9876543210";
    public static final String ANTHROPIC_MEDIUM_KEY = "sk-ant-" + "api03-AbCdEfGhIjKlMnOpQrSt";
    public static final String ANTHROPIC_SHORT_KEY = "sk-ant-" + "api03-AbCdEfGhIjKlMnOp";
    public static final String GOOGLE_API_KEY = "AIza" + "SyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r";
    public static final String GOOGLE_API_KEY_LONG = GOOGLE_API_KEY + "7s";
    public static final String GOOGLE_SHORT_KEY = "AIza" + "SyFAKE0123456789abcdef";
    public static final String GITHUB_TOKEN = "gh" + "p_" + "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";
    public static final String GITHUB_OAUTH_TOKEN = "gh" + "o_" + "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";
    public static final String GITHUB_FINE_GRAINED_TOKEN = "github_" + "pat_" + "11ABCDEFG0123456789_abcdefghij";
    public static final String AWS_ACCESS_KEY_ID = "AK" + "IA" + "IOSFODNN7EXAMPLE";
    public static final String SLACK_TOKEN = "xo" + "xb-" + "1234567890-abcdefghij";
    public static final String GROQ_KEY = "gs" + "k_" + "AbCdEfGhIjKlMnOpQrStUv01";
    public static final String HUGGING_FACE_TOKEN = "h" + "f_" + "AbCdEfGhIjKlMnOpQrStUv01";
    public static final String XAI_KEY = "xa" + "i-" + "AbCdEfGhIjKlMnOpQrStUv01";
    public static final String PERPLEXITY_KEY = "pp" + "lx-" + "AbCdEfGhIjKlMnOpQrStUv01";
    public static final String RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA " + "PRIVATE KEY-----";
    public static final String PRIVATE_KEY_HEADER = "-----BEGIN " + "PRIVATE KEY-----";

    // Ordinary text that only resembles a key (it contains "sk-" followed by a long word) and must not be masked.
    public static final String RISK_ASSESSMENT_GUIDELINES = "risk-" + "assessment-guidelines";
    public static final String TASK_MANAGEMENT_GLOB = "**/task-" + "management-service/**";
    public static final String TASK_SCHEDULER_PRODUCTION_EU = "task-" + "scheduler-production-eu";

    private FakeSecrets() {
    }
}
