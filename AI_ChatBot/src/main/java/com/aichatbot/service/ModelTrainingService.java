package com.aichatbot.service;

import com.aichatbot.entity.TrainingExample;
import com.aichatbot.ml.CosineSimilarityClassifier;
import com.aichatbot.ml.EnsembleClassifier;
import com.aichatbot.ml.IntentClassifier;
import com.aichatbot.ml.ModelEvaluator;
import com.aichatbot.nlp.NLPProcessor;
import com.aichatbot.nlp.SpellCorrector;
import com.aichatbot.repository.TrainingExampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the life cycle of the trained model.
 *
 * <p>Training reads every labelled example from the database, rebuilds the
 * spell-check vocabulary, runs each example through the NLP pipeline and fits
 * the ensemble classifier. The freshly trained model replaces the live one in a
 * single reference swap, so requests being served during a retrain always see a
 * complete model rather than a half-built one.</p>
 *
 * <p>Evaluation runs on a background thread after training because
 * cross-validation is quadratic in corpus size and should never delay startup
 * or hold up a user's retrain request.</p>
 */
@Service
public class ModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainingService.class);

    private final TrainingExampleRepository trainingExampleRepository;
    private final NLPProcessor nlpProcessor;
    private final SpellCorrector spellCorrector;
    private final ModelEvaluator evaluator = new ModelEvaluator();

    private final double bayesWeight;
    private final double similarityWeight;
    private final double keywordWeight;
    private final boolean evaluationEnabled;

    private volatile EnsembleClassifier model = new EnsembleClassifier();
    private volatile ModelEvaluator.Report report;
    private volatile LocalDateTime lastTrainedAt;
    private volatile int exampleCount;
    private volatile int intentCount;
    private volatile long lastTrainingMillis;
    private final AtomicInteger trainingRuns = new AtomicInteger();
    private final AtomicBoolean evaluating = new AtomicBoolean(false);

    public ModelTrainingService(TrainingExampleRepository trainingExampleRepository,
                                NLPProcessor nlpProcessor,
                                SpellCorrector spellCorrector,
                                @Value("${chatbot.weights.naive-bayes:0.35}") double bayesWeight,
                                @Value("${chatbot.weights.similarity:0.45}") double similarityWeight,
                                @Value("${chatbot.weights.keyword:0.20}") double keywordWeight,
                                @Value("${chatbot.evaluation.enabled:true}") boolean evaluationEnabled) {
        this.trainingExampleRepository = trainingExampleRepository;
        this.nlpProcessor = nlpProcessor;
        this.spellCorrector = spellCorrector;
        this.bayesWeight = bayesWeight;
        this.similarityWeight = similarityWeight;
        this.keywordWeight = keywordWeight;
        this.evaluationEnabled = evaluationEnabled;
    }

    /** Summary of the current model, returned by the status endpoint. */
    public record ModelStatus(String algorithm,
                              Map<String, Double> weights,
                              int trainingExamples,
                              int intents,
                              int featureVocabulary,
                              int spellVocabulary,
                              LocalDateTime lastTrainedAt,
                              long trainingTimeMs,
                              int trainingRuns,
                              boolean evaluating,
                              ModelEvaluator.Report evaluation) {
    }

    /**
     * Retrains from scratch on everything currently in the database.
     * Safe to call at any time, including while the bot is answering.
     */
    @Transactional(readOnly = true)
    public synchronized void train() {
        long start = System.currentTimeMillis();
        List<TrainingExample> examples = trainingExampleRepository.findAllWithIntent();

        if (examples.isEmpty()) {
            log.warn("No training examples in the database; the classifier stays empty.");
            this.model = new EnsembleClassifier(bayesWeight, similarityWeight, keywordWeight);
            this.exampleCount = 0;
            this.intentCount = 0;
            return;
        }

        // The spell checker must know the corpus vocabulary before features are
        // extracted, otherwise training text would be corrected against an
        // empty dictionary while user input is corrected against a full one.
        rebuildSpellVocabulary(examples);

        List<IntentClassifier.TrainingDocument> documents = new ArrayList<>(examples.size());
        Set<String> intents = new HashSet<>();

        for (TrainingExample example : examples) {
            List<String> features = nlpProcessor.extractFeatures(example.getExampleText());
            if (features.isEmpty()) {
                continue;
            }
            String intentName = example.getIntent().getName();
            intents.add(intentName);
            documents.add(new IntentClassifier.TrainingDocument(
                    intentName, example.getExampleText(), features));
        }

        EnsembleClassifier trained =
                new EnsembleClassifier(bayesWeight, similarityWeight, keywordWeight);
        trained.train(documents);

        this.model = trained;
        this.exampleCount = documents.size();
        this.intentCount = intents.size();
        this.lastTrainedAt = LocalDateTime.now();
        this.lastTrainingMillis = System.currentTimeMillis() - start;
        this.trainingRuns.incrementAndGet();

        log.info("Model trained on {} examples across {} intents in {} ms",
                exampleCount, intentCount, lastTrainingMillis);

        if (evaluationEnabled) {
            evaluateInBackground(documents);
        }
    }

    /** Collects every word in the corpus so typos can be corrected towards it. */
    private void rebuildSpellVocabulary(List<TrainingExample> examples) {
        Set<String> vocabulary = new HashSet<>();
        for (TrainingExample example : examples) {
            vocabulary.addAll(nlpProcessor.vocabularyOf(example.getExampleText()));
        }
        spellCorrector.loadVocabulary(vocabulary);
    }

    /**
     * Runs cross-validation off the request thread. Only one evaluation runs at
     * a time; a retrain during evaluation simply skips the new run and the next
     * one picks up the newer corpus.
     */
    private void evaluateInBackground(List<IntentClassifier.TrainingDocument> documents) {
        if (!evaluating.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                ModelEvaluator.Report result = evaluator.crossValidate(documents,
                        () -> new EnsembleClassifier(bayesWeight, similarityWeight, keywordWeight));
                this.report = result;
                log.info("Cross-validation complete: accuracy {}%, macro F1 {} over {} samples in {} ms",
                        Math.round(result.accuracy() * 1000) / 10.0,
                        result.macroF1(), result.sampleCount(), result.evaluationMillis());
            } catch (RuntimeException e) {
                log.warn("Model evaluation failed: {}", e.getMessage());
            } finally {
                evaluating.set(false);
            }
        }, "model-evaluator");
        worker.setDaemon(true);
        worker.start();
    }

    /** The live model. Never null, though it may be untrained before startup completes. */
    public EnsembleClassifier model() {
        return model;
    }

    /** Ranked intent predictions for an already analysed message. */
    public List<IntentClassifier.Prediction> predict(List<String> features) {
        return model.predict(features);
    }

    /** The training examples most similar to a message, used to explain a match. */
    public List<CosineSimilarityClassifier.Neighbour> nearestExamples(List<String> features) {
        return model.nearestExamples(features);
    }

    public ModelStatus status() {
        EnsembleClassifier current = model;
        return new ModelStatus(
                current.name(),
                current.weights(),
                exampleCount,
                intentCount,
                current.naiveBayes().vocabularySize(),
                spellCorrector.vocabularySize(),
                lastTrainedAt,
                lastTrainingMillis,
                trainingRuns.get(),
                evaluating.get(),
                report);
    }

    public ModelEvaluator.Report evaluation() {
        return report;
    }

    public boolean isTrained() {
        return exampleCount > 0;
    }
}
