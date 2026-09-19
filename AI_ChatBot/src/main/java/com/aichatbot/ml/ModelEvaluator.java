package com.aichatbot.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Measures how well a classifier actually performs instead of assuming it works.
 *
 * <p>Uses leave-one-out cross-validation: each training example is held out in
 * turn, the model is retrained on everything else, and the held-out example is
 * predicted. With a small hand-written FAQ corpus this gives a far more honest
 * number than testing on the same data the model memorised.</p>
 *
 * <p>Reports overall accuracy plus macro-averaged precision, recall and F1, and
 * lists the intent pairs the model most often confuses, which is the practical
 * guide to which FAQ needs more training examples.</p>
 */
public class ModelEvaluator {

    /** Per-intent quality figures. */
    public record IntentMetrics(String intent,
                                int support,
                                double precision,
                                double recall,
                                double f1) {
    }

    /** A wrong prediction, kept so the report can show what to fix. */
    public record Confusion(String expected, String predicted, int count) {
    }

    /** The full evaluation report surfaced by the metrics endpoint. */
    public record Report(String method,
                         int sampleCount,
                         int intentCount,
                         double accuracy,
                         double macroPrecision,
                         double macroRecall,
                         double macroF1,
                         double averageConfidence,
                         List<IntentMetrics> perIntent,
                         List<Confusion> topConfusions,
                         long evaluationMillis) {
    }

    private static final int MAX_SAMPLES = 1200;

    /**
     * Runs leave-one-out cross-validation over the corpus.
     *
     * @param documents     the labelled corpus
     * @param modelSupplier produces a fresh untrained model for every fold
     */
    public Report crossValidate(List<IntentClassifier.TrainingDocument> documents,
                                Supplier<IntentClassifier> modelSupplier) {
        long start = System.currentTimeMillis();

        if (documents == null || documents.size() < 4) {
            return new Report("leave-one-out cross-validation", documents == null ? 0 : documents.size(),
                    0, 0, 0, 0, 0, 0, List.of(), List.of(), 0);
        }

        // Cross-validation is O(n^2); above the cap fall back to a 80/20 split
        // so startup never stalls on a very large corpus.
        boolean useHoldout = documents.size() > MAX_SAMPLES;

        Map<String, Integer> truePositives = new HashMap<>();
        Map<String, Integer> falsePositives = new HashMap<>();
        Map<String, Integer> falseNegatives = new HashMap<>();
        Map<String, Integer> support = new HashMap<>();
        Map<String, Integer> confusionCounts = new LinkedHashMap<>();

        int correct = 0;
        int evaluated = 0;
        double confidenceTotal = 0.0;

        if (useHoldout) {
            int split = (int) (documents.size() * 0.8);
            List<IntentClassifier.TrainingDocument> trainingSet = documents.subList(0, split);
            List<IntentClassifier.TrainingDocument> testSet = documents.subList(split, documents.size());
            IntentClassifier model = modelSupplier.get();
            model.train(trainingSet);

            for (IntentClassifier.TrainingDocument document : testSet) {
                Outcome outcome = score(model, document);
                evaluated++;
                confidenceTotal += outcome.confidence();
                correct += record(outcome, document.intent(), truePositives, falsePositives,
                        falseNegatives, support, confusionCounts);
            }
        } else {
            for (int held = 0; held < documents.size(); held++) {
                List<IntentClassifier.TrainingDocument> trainingSet = new ArrayList<>(documents);
                IntentClassifier.TrainingDocument document = trainingSet.remove(held);

                IntentClassifier model = modelSupplier.get();
                model.train(trainingSet);

                Outcome outcome = score(model, document);
                evaluated++;
                confidenceTotal += outcome.confidence();
                correct += record(outcome, document.intent(), truePositives, falsePositives,
                        falseNegatives, support, confusionCounts);
            }
        }

        List<IntentMetrics> perIntent = new ArrayList<>();
        double precisionSum = 0.0;
        double recallSum = 0.0;
        double f1Sum = 0.0;

        for (String intent : support.keySet()) {
            int tp = truePositives.getOrDefault(intent, 0);
            int fp = falsePositives.getOrDefault(intent, 0);
            int fn = falseNegatives.getOrDefault(intent, 0);

            double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
            double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
            double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);

            precisionSum += precision;
            recallSum += recall;
            f1Sum += f1;
            perIntent.add(new IntentMetrics(intent, support.get(intent),
                    round(precision), round(recall), round(f1)));
        }

        perIntent.sort(Comparator.comparingDouble(IntentMetrics::f1));
        int classes = Math.max(support.size(), 1);

        List<Confusion> confusions = confusionCounts.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("->", 2);
                    return new Confusion(parts[0], parts.length > 1 ? parts[1] : "unknown", entry.getValue());
                })
                .sorted(Comparator.comparingInt(Confusion::count).reversed())
                .limit(8)
                .toList();

        return new Report(
                useHoldout ? "80/20 holdout split" : "leave-one-out cross-validation",
                evaluated,
                support.size(),
                round(evaluated == 0 ? 0 : (double) correct / evaluated),
                round(precisionSum / classes),
                round(recallSum / classes),
                round(f1Sum / classes),
                round(evaluated == 0 ? 0 : confidenceTotal / evaluated),
                perIntent,
                confusions,
                System.currentTimeMillis() - start);
    }

    private record Outcome(String predicted, double confidence) {
    }

    private Outcome score(IntentClassifier model, IntentClassifier.TrainingDocument document) {
        List<IntentClassifier.Prediction> predictions = model.predict(document.features());
        if (predictions.isEmpty()) {
            return new Outcome("none", 0.0);
        }
        IntentClassifier.Prediction top = predictions.get(0);
        return new Outcome(top.intent(), top.confidence());
    }

    /** Updates the counters and returns 1 when the prediction was correct. */
    private int record(Outcome outcome,
                       String expected,
                       Map<String, Integer> truePositives,
                       Map<String, Integer> falsePositives,
                       Map<String, Integer> falseNegatives,
                       Map<String, Integer> support,
                       Map<String, Integer> confusionCounts) {
        support.merge(expected, 1, Integer::sum);

        if (outcome.predicted().equals(expected)) {
            truePositives.merge(expected, 1, Integer::sum);
            return 1;
        }
        falseNegatives.merge(expected, 1, Integer::sum);
        falsePositives.merge(outcome.predicted(), 1, Integer::sum);
        confusionCounts.merge(expected + "->" + outcome.predicted(), 1, Integer::sum);
        return 0;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
