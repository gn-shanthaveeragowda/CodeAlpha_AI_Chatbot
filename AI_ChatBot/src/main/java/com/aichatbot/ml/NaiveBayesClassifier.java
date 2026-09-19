package com.aichatbot.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multinomial Naive Bayes text classifier with Laplace (add-alpha) smoothing.
 *
 * <p>This is the statistical learning half of the chatbot. It learns
 * P(intent) and P(word | intent) from the training examples, then applies
 * Bayes' rule to rank intents for a new message:</p>
 *
 * <pre>
 *   score(c) = log P(c) + SUM over words w of log P(w | c)
 *   P(w | c) = (count(w, c) + alpha) / (totalWords(c) + alpha * |V|)
 * </pre>
 *
 * <p>Log space is used so that multiplying many small probabilities cannot
 * underflow. The final log scores are converted back to comparable
 * probabilities with a softmax.</p>
 */
public class NaiveBayesClassifier implements IntentClassifier {

    private final double alpha;

    private final Map<String, Double> logPrior = new HashMap<>();
    private final Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
    private final Map<String, Integer> totalWords = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private boolean trained;

    public NaiveBayesClassifier() {
        this(1.0);
    }

    public NaiveBayesClassifier(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void train(List<TrainingDocument> documents) {
        logPrior.clear();
        wordCounts.clear();
        totalWords.clear();
        vocabulary.clear();
        trained = false;

        if (documents == null || documents.isEmpty()) {
            return;
        }

        Map<String, Integer> documentsPerIntent = new HashMap<>();

        for (TrainingDocument document : documents) {
            String intent = document.intent();
            documentsPerIntent.merge(intent, 1, Integer::sum);

            Map<String, Integer> counts =
                    wordCounts.computeIfAbsent(intent, key -> new HashMap<>());
            for (String feature : document.features()) {
                counts.merge(feature, 1, Integer::sum);
                totalWords.merge(intent, 1, Integer::sum);
                vocabulary.add(feature);
            }
        }

        int total = documents.size();
        documentsPerIntent.forEach((intent, count) ->
                logPrior.put(intent, Math.log((double) count / total)));

        trained = true;
    }

    @Override
    public List<Prediction> predict(List<String> features) {
        if (!trained || features == null || features.isEmpty()) {
            return List.of();
        }

        Map<String, Double> logScores = new HashMap<>();
        int vocabularySize = Math.max(vocabulary.size(), 1);

        // Count how much of the message the model has actually seen before.
        // With no known features at all, the scores would be the class priors
        // and the largest intent would always "win" with nothing behind it, so
        // the model abstains instead and lets the caller fall back.
        int knownFeatures = 0;
        for (String feature : features) {
            if (vocabulary.contains(feature)) {
                knownFeatures++;
            }
        }
        if (knownFeatures == 0) {
            return List.of();
        }

        for (String intent : logPrior.keySet()) {
            double score = logPrior.get(intent);
            Map<String, Integer> counts = wordCounts.getOrDefault(intent, Map.of());
            int intentTotal = totalWords.getOrDefault(intent, 0);
            double denominator = intentTotal + alpha * vocabularySize;

            for (String feature : features) {
                // Features never seen anywhere are uninformative: skip them so a
                // long unknown message is not penalised into a flat distribution.
                if (!vocabulary.contains(feature)) {
                    continue;
                }
                int count = counts.getOrDefault(feature, 0);
                score += Math.log((count + alpha) / denominator);
            }
            logScores.put(intent, score);
        }

        // A verdict based on one recognised word out of six is far weaker than
        // one based on all six, and the softmax alone cannot express that.
        double evidence = (double) knownFeatures / features.size();
        return softmax(logScores, evidence);
    }

    /**
     * Converts log scores into a normalized probability distribution, scaled by
     * how much of the message the model recognised.
     */
    private List<Prediction> softmax(Map<String, Double> logScores, double evidence) {
        if (logScores.isEmpty()) {
            return List.of();
        }
        double max = logScores.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.0);

        Map<String, Double> exponentials = new HashMap<>();
        double sum = 0.0;
        for (Map.Entry<String, Double> entry : logScores.entrySet()) {
            double value = Math.exp(entry.getValue() - max);
            exponentials.put(entry.getKey(), value);
            sum += value;
        }

        final double denominator = sum == 0.0 ? 1.0 : sum;
        List<Prediction> predictions = new ArrayList<>(exponentials.size());
        exponentials.forEach((intent, value) ->
                predictions.add(new Prediction(intent, (value / denominator) * evidence)));
        predictions.sort(Comparator.comparingDouble(Prediction::confidence).reversed());
        return predictions;
    }

    @Override
    public String name() {
        return "Multinomial Naive Bayes";
    }

    public int vocabularySize() {
        return vocabulary.size();
    }

    public int intentCount() {
        return logPrior.size();
    }

    public boolean isTrained() {
        return trained;
    }
}
