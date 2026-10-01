package dev.apidocs.core.pipeline;

@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = event -> {
    };

    void onEvent(ProgressEvent event);
}
