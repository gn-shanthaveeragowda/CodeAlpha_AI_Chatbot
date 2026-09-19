package com.aichatbot.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TfIdfVectorizer")
class TfIdfVectorizerTest {

    @Test
    @DisplayName("gives identical messages a cosine similarity of 1")
    void identicalMessagesAreMaximallySimilar() {
        TfIdfVectorizer vectorizer = new TfIdfVectorizer();
        vectorizer.fit(List.of(List.of("a", "b"), List.of("a", "c")));

        Map<String, Double> first = vectorizer.transform(List.of("a", "b"));
        Map<String, Double> second = vectorizer.transform(List.of("a", "b"));

        assertEquals(1.0, TfIdfVectorizer.cosineSimilarity(first, second), 0.0001);
    }

    @Test
    @DisplayName("gives messages with no shared words a cosine similarity of 0")
    void disjointMessagesAreDissimilar() {
        TfIdfVectorizer vectorizer = new TfIdfVectorizer();
        vectorizer.fit(List.of(List.of("a", "b"), List.of("a", "c")));

        Map<String, Double> first = vectorizer.transform(List.of("a", "b"));
        Map<String, Double> third = vectorizer.transform(List.of("c"));

        assertEquals(0.0, TfIdfVectorizer.cosineSimilarity(first, third), 0.0001);
    }

    @Test
    @DisplayName("an empty vector has zero similarity to anything")
    void emptyVectorHasNoSimilarity() {
        TfIdfVectorizer vectorizer = new TfIdfVectorizer();
        vectorizer.fit(List.of(List.of("a")));
        assertEquals(0.0, TfIdfVectorizer.cosineSimilarity(Map.of(), vectorizer.transform(List.of("a"))));
    }

    @Test
    @DisplayName("produces a unit-length vector for a non-empty feature list")
    void producesUnitLengthVector() {
        TfIdfVectorizer vectorizer = new TfIdfVectorizer();
        vectorizer.fit(List.of(List.of("a", "b", "c"), List.of("a")));
        Map<String, Double> vector = vectorizer.transform(List.of("a", "b"));

        double magnitude = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
        assertEquals(1.0, magnitude, 0.0001);
    }

    @Test
    @DisplayName("rarer words receive a higher idf weight than common ones")
    void rareWordsWeightMoreThanCommonOnes() {
        TfIdfVectorizer vectorizer = new TfIdfVectorizer();
        // "common" appears in every document, "rare" in only one.
        vectorizer.fit(List.of(
                List.of("common", "rare"),
                List.of("common"),
                List.of("common")));

        Map<String, Double> vector = vectorizer.transform(List.of("common", "rare"));
        assertTrue(vector.get("rare") > vector.get("common"),
                "expected the rarer term to carry more weight in the vector");
    }
}
