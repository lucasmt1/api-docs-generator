package dev.apidocs.cli;

final class ExitCodes {

    static final int OK = 0;
    static final int USAGE = 1;
    static final int ANALYSIS = 2;
    static final int LLM = 3;
    static final int CANCELLED = 130;

    private ExitCodes() {
    }
}
