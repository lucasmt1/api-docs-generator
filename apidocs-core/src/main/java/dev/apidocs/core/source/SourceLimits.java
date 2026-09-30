package dev.apidocs.core.source;

public record SourceLimits(int maxFiles, long maxFileBytes) {

    public static final SourceLimits DEFAULT = new SourceLimits(5_000, 1_048_576L);
}
