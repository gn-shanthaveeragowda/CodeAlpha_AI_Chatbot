package com.aichatbot.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nearest-neighbour classifier over TF-IDF vectors.
 *
 * <p>Every training example becomes a vector. An incoming message is vectorized
 * the same way and compared to all of them with cosine similarity. The k most
 * similar neighbours vote for their intent, weighted by similarity, so a single
 * strong match wins but several moderate matches for one intent also count.</p>
 *
 * <p>It complements Naive Bayes well: Bayes generalises from word statistics
 * across a whole intent, while this model matches the exact phrasing of
 * individual questions, which is what users usually type.</p>
 */
public class CosineSimilarityClassifier implements IntentClassifier {

    private final TfIdfVectorizer vectorizer = new TfIdfVectorizer();
    private final List<String> labels = new ArrayList<>();
    private final List<Map<String, Double>> vectors = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private final int neighbours;

    public CosineSimilarityClassifier() {
        this(3);
    }

    public CosineSimilarityClassifier(int neighbours) {
        this.neighbours = Math.max(1, neighbours);
    }

    @Override
    public void train(List<TrainingDocument> documents) {
        labels.clear();
        vectors.clear();
        texts.clear();

        if (documents == null || documents.isEmpty()) {
            return;
        }

        List<List<String>> corpus = new ArrayList<>(documents.size());
        for (TrainingDocument document : documents) {
            corpus.add(document.features());
        }
        vectorizer.fit(corpus);

        for (TrainingDocument document : documents) {
            labels.add(document.intent());
            texts.add(document.text());
            vectors.add(vectorizer.transform(document.features()));
        }
    }

    @Override
    public List<Prediction> predict(List<String> features) {
        List<Neighbour> ranked = nearestNeighbours(features);
        if (ranked.isEmpty()) {
            return List.of();
        }

        Map<String, Double> bestSimilarity = new HashMap<>();
        Map<String, Integer> support = new HashMap<>();
        int considered = Math.min(neighbours, ranked.size());

        for (int i = 0; i < considered; i++) {
            Neighbour neighbour = ranked.get(i);
            bestSimilarity.merge(neighbour.intent(), neighbour.similarity(), Math::max);
            support.merge(neighbour.intent(), 1, Integer::sum);
        }
        // Intents outside the top k still get their best similarity recorded so
        // the caller can show them as weaker alternatives.
        for (Neighbour neighbour : ranked) {
            bestSimilarity.merge(neighbour.intent(), neighbour.similarity(), Math::max);
        }

        List<Prediction> predictions = new ArrayList<>(bestSimilarity.size());
        bestSimilarity.forEach((intent, similarity) -> {
            // Several of the nearest neighbours agreeing is mild extra evidence.
            int agreeing = support.getOrDefault(intent, 0);
            double bonus = agreeing > 1 ? 0.05 * (agreeing - 1) : 0.0;
            predictions.add(new Prediction(intent, Math.min(1.0, similarity + bonus)));
        });
        predictions.sort(Comparator.comparingDouble(Prediction::confidence).reversed());
        return predictions;
    }

    /** Returns every training example ordered by similarity, closest first. */
    public List<Neighbour> nearestNeighbours(List<String> features) {
        if (vectors.isEmpty() || features == null || features.isEmpty()) {
            return List.of();
        }
        Map<String, Double> query = vectorizer.transform(features);
        List<Neighbour> ranked = new ArrayList<>(vectors.size());

        for (int i = 0; i < vectors.size(); i++) {
            double similarity = TfIdfVectorizer.cosineSimilarity(query, vectors.get(i));
            if (similarity > 0.0) {
                ranked.add(new Neighbour(labels.get(i), texts.get(i), similarity));
            }
        }
        ranked.sort(Comparator.comparingDouble(Neighbour::similarity).reversed());
        return ranked;
    }

    /** A training example together with its similarity to the query. */
    public record Neighbour(String intent, String text, double similarity) {
    }

    @Override
    public String name() {
        return "TF-IDF Cosine Similarity (k=" + neighbours + ")";
    }

    public int vocabularySize() {
        return vectorizer.vocabularySize();
    }

    public int exampleCount() {
        return vectors.size();
    }
}
