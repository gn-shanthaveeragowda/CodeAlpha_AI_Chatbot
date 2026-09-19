package com.aichatbot.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EnsembleClassifier")
class EnsembleClassifierTest {

    private final EnsembleClassifier classifier = new EnsembleClassifier();

    @BeforeEach
    void trainOnTwoIntents() {
        classifier.train(List.of(
                new IntentClassifier.TrainingDocument("greeting", "hi", List.of("hi")),
                new IntentClassifier.TrainingDocument("greeting", "hello there", List.of("hello", "there")),
                new IntentClassifier.TrainingDocument("farewell", "bye", List.of("bye")),
                new IntentClassifier.TrainingDocument("farewell", "goodbye now", List.of("goodby", "now"))));
    }

    @Test
    @DisplayName("an exact match to a stored example short-circuits to full confidence")
    void exactMatchIsFullConfidence() {
        List<IntentClassifier.Prediction> predictions = classifier.predict(List.of("hello", "there"));
        assertEquals("greeting", predictions.get(0).intent());
        assertEquals(1.0, predictions.get(0).confidence(), 0.0001);
    }

    @Test
    @DisplayName("normalised weights always sum to one, whatever raw weights are supplied")
    void weightsAreNormalised() {
        EnsembleClassifier custom = new EnsembleClassifier(1.0, 1.0, 2.0);
        double total = custom.weights().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, total, 0.001);
    }

    @Test
    @DisplayName("an untrained ensemble predicts nothing")
    void untrainedEnsemblePredictsNothing() {
        EnsembleClassifier fresh = new EnsembleClassifier();
        assertTrue(fresh.predict(List.of("hi")).isEmpty());
        assertEquals(0, fresh.documentCount());
    }

    @Test
    @DisplayName("reports the number of documents it was trained on")
    void reportsDocumentCount() {
        assertEquals(4, classifier.documentCount());
    }

    @Test
    @DisplayName("exposes the nearest stored examples for explaining a decision")
    void exposesNearestExamples() {
        List<CosineSimilarityClassifier.Neighbour> neighbours = classifier.nearestExamples(List.of("hi"));
        assertTrue(!neighbours.isEmpty());
        assertEquals("greeting", neighbours.get(0).intent());
    }
}
