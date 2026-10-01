package dev.apidocs.core.pipeline;

/** Stages of the reference diagram, in execution order. */
public enum Stage {
    SOURCE_LOADING, PARSING, EXTRACTION, CONTEXT_BUILDING, AI_PROCESSING, RENDERING, DELIVERY
}
