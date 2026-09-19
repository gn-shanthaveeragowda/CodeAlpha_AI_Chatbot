package com.aichatbot.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CosineSimilarityClassifier")
class CosineSimilarityClassifierTest {

    private final CosineSimilarityClassifier classifier = new CosineSimilarityClassifier(2);

    @BeforeEach
    void trainOnTwoIntents() {
        classifier.train(List.of(
                new IntentClassifier.TrainingDocument("greeting", "hi", List.of("hi")),
                new IntentClassifier.TrainingDocument("greeting", "hello there", List.of("hello", "there")),
                new IntentClassifier.TrainingDocument("farewell", "bye", List.of("bye")),
                new IntentClassifier.TrainingDocument("farewell", "goodbye now", List.of("goodby", "now"))));
    }

    @Test
    @DisplayName("an exact match to a training example scores full confidence")
    void exactMatchScoresFullConfidence() {
        List<IntentClassifier.Prediction> predictions = classifier.predict(List.of("hi"));
        assertEquals("greeting", predictions.get(0).intent());
        assertEquals(1.0, predictions.get(0).confidence(), 0.0001);
    }

    @Test
    @DisplayName("nearest neighbours are ordered by similarity, closest first")
    void nearestNeighboursAreOrdered() {
        List<CosineSimilarityClassifier.Neighbour> neighbours = classifier.nearestNeighbours(List.of("hi"));
        assertEquals("greeting", neighbours.get(0).intent());
        assertEquals("hi", neighbours.get(0).text());
        assertEquals(1.0, neighbours.get(0).similarity(), 0.0001);
    }

    @Test
    @DisplayName("returns nothing for a message sharing no vocabulary with training data")
    void returnsNothingForDisjointMessage() {
        assertTrue(classifier.predict(List.of("zzz")).isEmpty());
        assertTrue(classifier.nearestNeighbours(List.of("zzz")).isEmpty());
    }

    @Test
    @DisplayName("reports how many examples and vocabulary terms it holds")
    void reportsTrainingSize() {
        assertEquals(4, classifier.exampleCount());
        assertTrue(classifier.vocabularySize() > 0);
    }
}
