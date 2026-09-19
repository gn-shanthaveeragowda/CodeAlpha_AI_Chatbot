package com.aichatbot.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines three independent signals into one ranked list of intents.
 *
 * <ol>
 *   <li><b>Naive Bayes</b> generalises from word statistics across an intent.</li>
 *   <li><b>TF-IDF cosine similarity</b> matches the exact phrasing of examples.</li>
 *   <li><b>Keyword coverage</b> measures how much of the message the intent's
 *       vocabulary actually explains, which keeps unrelated questions from
 *       scoring highly just because the models must pick something.</li>
 * </ol>
 *
 * <p>A weighted blend beats any single model here: Bayes alone over-predicts
 * intents with many training examples, and similarity alone is brittle when a
 * user phrases a question in a way no example covers. An exact match against a
 * stored example short-circuits the whole blend with full confidence, which is
 * the rule-based path through the classifier.</p>
 */
public class EnsembleClassifier implements IntentClassifier {

    private final NaiveBayesClassifier naiveBayes;
    private final CosineSimilarityClassifier similarity;

    private final double bayesWeight;
    private final double similarityWeight;
    private final double keywordWeight;

    private final Map<String, Set<String>> intentVocabulary = new HashMap<>();
    private final Map<String, String> exactMatches = new HashMap<>();
    private int documentCount;

    public EnsembleClassifier() {
        this(0.35, 0.45, 0.20);
    }

    public EnsembleClassifier(double bayesWeight, double similarityWeight, double keywordWeight) {
        double total = bayesWeight + similarityWeight + keywordWeight;
        double safeTotal = total <= 0 ? 1.0 : total;
        this.bayesWeight = bayesWeight / safeTotal;
        this.similarityWeight = similarityWeight / safeTotal;
        this.keywordWeight = keywordWeight / safeTotal;
        this.naiveBayes = new NaiveBayesClassifier();
        this.similarity = new CosineSimilarityClassifier(3);
    }

    @Override
    public void train(List<TrainingDocument> documents) {
        intentVocabulary.clear();
        exactMatches.clear();
        documentCount = documents == null ? 0 : documents.size();

        if (documents == null || documents.isEmpty()) {
            naiveBayes.train(List.of());
            similarity.train(List.of());
            return;
        }

        naiveBayes.train(documents);
        similarity.train(documents);

        for (TrainingDocument document : documents) {
            intentVocabulary
                    .computeIfAbsent(document.intent(), key -> new HashSet<>())
                    .addAll(document.features());
            exactMatches.putIfAbsent(fingerprint(document.features()), document.intent());
        }
    }

    @Override
    public List<Prediction> predict(List<String> features) {
        if (documentCount == 0 || features == null || features.isEmpty()) {
            return List.of();
        }

        String exact = exactMatches.get(fingerprint(features));
        if (exact != null) {
            return List.of(new Prediction(exact, 1.0));
        }

        Map<String, Double> bayesScores = toMap(naiveBayes.predict(features));
        Map<String, Double> similarityScores = toMap(similarity.predict(features));

        Set<String> candidates = new LinkedHashSet<>();
        candidates.addAll(bayesScores.keySet());
        candidates.addAll(similarityScores.keySet());

        List<Prediction> blended = new ArrayList<>(candidates.size());
        for (String intent : candidates) {
            double score = bayesWeight * bayesScores.getOrDefault(intent, 0.0)
                    + similarityWeight * similarityScores.getOrDefault(intent, 0.0)
                    + keywordWeight * keywordCoverage(intent, features);
            blended.add(new Prediction(intent, Math.min(1.0, score)));
        }

        blended.sort(Comparator.comparingDouble(Prediction::confidence).reversed());
        return blended;
    }

    /**
     * Fraction of the message's distinct features that this intent has actually
     * been trained on. Bigrams count double because matching a whole phrase is
     * much stronger evidence than matching one loose word.
     */
    private double keywordCoverage(String intent, List<String> features) {
        Set<String> vocabulary = intentVocabulary.get(intent);
        if (vocabulary == null || vocabulary.isEmpty()) {
            return 0.0;
        }
        double matched = 0.0;
        double total = 0.0;
        for (String feature : new LinkedHashSet<>(features)) {
            double weight = feature.indexOf('_') >= 0 ? 2.0 : 1.0;
            total += weight;
            if (vocabulary.contains(feature)) {
                matched += weight;
            }
        }
        return total == 0.0 ? 0.0 : matched / total;
    }

    private Map<String, Double> toMap(List<Prediction> predictions) {
        Map<String, Double> map = new HashMap<>();
        for (Prediction prediction : predictions) {
            map.put(prediction.intent(), prediction.confidence());
        }
        return map;
    }

    /** Order-independent signature of a feature list, used for exact matching. */
    private String fingerprint(List<String> features) {
        return new java.util.TreeSet<>(features).toString();
    }

    /** Exposed so the service layer can show which stored question matched. */
    public List<CosineSimilarityClassifier.Neighbour> nearestExamples(List<String> features) {
        return similarity.nearestNeighbours(features);
    }

    @Override
    public String name() {
        return "Ensemble (Naive Bayes + TF-IDF Cosine + Keyword Coverage)";
    }

    public Map<String, Double> weights() {
        return Map.of(
                "naiveBayes", round(bayesWeight),
                "cosineSimilarity", round(similarityWeight),
                "keywordCoverage", round(keywordWeight));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public NaiveBayesClassifier naiveBayes() {
        return naiveBayes;
    }

    public CosineSimilarityClassifier similarity() {
        return similarity;
    }

    public int documentCount() {
        return documentCount;
    }
}
