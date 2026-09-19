package com.aichatbot.ml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns feature lists into L2-normalized TF-IDF vectors.
 *
 * <p>Term frequency rewards words that appear often in a message, while inverse
 * document frequency discounts words that appear in nearly every training
 * example. That is what stops filler words from dominating the similarity
 * score and lets a rare word such as "polymorphism" decide the match.</p>
 *
 * <pre>
 *   idf(t) = log((N + 1) / (df(t) + 1)) + 1
 *   tfidf(t, d) = (1 + log tf(t, d)) * idf(t)
 * </pre>
 */
public class TfIdfVectorizer {

    private final Map<String, Double> idf = new HashMap<>();
    private int documentCount;

    /** Learns the inverse document frequency of every feature in the corpus. */
    public void fit(List<List<String>> documents) {
        idf.clear();
        documentCount = documents.size();
        if (documentCount == 0) {
            return;
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> document : documents) {
            Set<String> unique = new HashSet<>(document);
            for (String feature : unique) {
                documentFrequency.merge(feature, 1, Integer::sum);
            }
        }

        documentFrequency.forEach((feature, frequency) ->
                idf.put(feature, Math.log((documentCount + 1.0) / (frequency + 1.0)) + 1.0));
    }

    /**
     * Projects a feature list into the learned vector space. Unseen features are
     * given the maximum idf, treating them as highly specific rather than
     * discarding them.
     */
    public Map<String, Double> transform(List<String> features) {
        Map<String, Double> vector = new HashMap<>();
        if (features == null || features.isEmpty()) {
            return vector;
        }

        Map<String, Integer> termFrequency = new HashMap<>();
        for (String feature : features) {
            termFrequency.merge(feature, 1, Integer::sum);
        }

        double defaultIdf = Math.log(documentCount + 1.0) + 1.0;
        termFrequency.forEach((feature, count) -> {
            double weight = (1.0 + Math.log(count)) * idf.getOrDefault(feature, defaultIdf);
            vector.put(feature, weight);
        });

        return normalize(vector);
    }

    /** Scales a vector to unit length so cosine similarity is a plain dot product. */
    private Map<String, Double> normalize(Map<String, Double> vector) {
        double magnitude = 0.0;
        for (double value : vector.values()) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude == 0.0) {
            return vector;
        }
        final double length = magnitude;
        vector.replaceAll((feature, value) -> value / length);
        return vector;
    }

    /**
     * Cosine similarity of two unit-length vectors. Iterates over the smaller
     * map so the cost stays proportional to the shorter message.
     */
    public static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> smaller = a.size() <= b.size() ? a : b;
        Map<String, Double> larger = smaller == a ? b : a;

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            Double other = larger.get(entry.getKey());
            if (other != null) {
                dotProduct += entry.getValue() * other;
            }
        }
        return Math.max(0.0, Math.min(1.0, dotProduct));
    }

    public int vocabularySize() {
        return idf.size();
    }

    public int documentCount() {
        return documentCount;
    }
}
