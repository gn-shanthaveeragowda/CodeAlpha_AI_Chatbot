package com.aichatbot.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NaiveBayesClassifier")
class NaiveBayesClassifierTest {

    private final NaiveBayesClassifier classifier = new NaiveBayesClassifier();

    @BeforeEach
    void trainOnTwoIntents() {
        classifier.train(List.of(
                new IntentClassifier.TrainingDocument("greeting", "hi", List.of("hi")),
                new IntentClassifier.TrainingDocument("greeting", "hello there", List.of("hello", "there")),
                new IntentClassifier.TrainingDocument("farewell", "bye", List.of("bye")),
                new IntentClassifier.TrainingDocument("farewell", "goodbye now", List.of("goodby", "now"))));
    }

    @Test
    @DisplayName("ranks the intent whose vocabulary matches highest")
    void ranksMatchingIntentFirst() {
        List<IntentClassifier.Prediction> predictions = classifier.predict(List.of("hi"));
        assertEquals("greeting", predictions.get(0).intent());
        assertTrue(predictions.get(0).confidence() > predictions.get(1).confidence());
    }

    @Test
    @DisplayName("confidences form a probability distribution that sums to one")
    void confidencesSumToOne() {
        List<IntentClassifier.Prediction> predictions = classifier.predict(List.of("bye"));
        double total = predictions.stream().mapToDouble(IntentClassifier.Prediction::confidence).sum();
        assertEquals(1.0, total, 0.001);
    }

    @Test
    @DisplayName("abstains on a completely unrecognised word instead of guessing from priors")
    void abstainsOnUnknownWord() {
        assertTrue(classifier.predict(List.of("zzz")).isEmpty());
    }

    @Test
    @DisplayName("returns no predictions for an empty feature list")
    void returnsEmptyForNoFeatures() {
        assertTrue(classifier.predict(List.of()).isEmpty());
    }

    @Test
    @DisplayName("reports vocabulary and intent counts learned from training")
    void reportsTrainingSize() {
        assertEquals(2, classifier.intentCount());
        assertTrue(classifier.vocabularySize() >= 4);
        assertTrue(classifier.isTrained());
    }

    @Test
    @DisplayName("resets to untrained state when trained on an empty list")
    void resetsOnEmptyTraining() {
        classifier.train(List.of());
        assertTrue(!classifier.isTrained());
        assertTrue(classifier.predict(List.of("hi")).isEmpty());
    }
}
