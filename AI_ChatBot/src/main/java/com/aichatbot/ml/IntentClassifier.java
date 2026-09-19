package com.aichatbot.ml;

import java.util.List;

/**
 * Common contract for every classifier in the project so they can be trained,
 * evaluated and swapped uniformly.
 */
public interface IntentClassifier {

    /** One labelled training utterance after NLP feature extraction. */
    record TrainingDocument(String intent, String text, List<String> features) {
    }

    /** A candidate intent with its confidence in the range 0 to 1. */
    record Prediction(String intent, double confidence) {
    }

    /** Fits the model to the supplied labelled data. */
    void train(List<TrainingDocument> documents);

    /**
     * Ranks candidate intents for the given features, highest confidence first.
     * Returns an empty list when the model has no opinion at all.
     */
    List<Prediction> predict(List<String> features);

    /** Human readable model name, surfaced in the metrics endpoint. */
    String name();
}
