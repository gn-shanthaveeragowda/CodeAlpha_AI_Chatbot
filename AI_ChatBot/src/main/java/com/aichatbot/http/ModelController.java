package com.aichatbot.http;

import com.aichatbot.ml.CosineSimilarityClassifier;
import com.aichatbot.ml.IntentClassifier;
import com.aichatbot.ml.ModelEvaluator;
import com.aichatbot.nlp.NLPProcessor;
import com.aichatbot.service.ModelTrainingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspection and control of the trained model.
 *
 * <p>The analyze endpoint is the useful one during a demo: it shows every stage
 * of the NLP pipeline and every classifier score for a message, without the bot
 * actually answering or recording anything.</p>
 */
@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final ModelTrainingService modelTrainingService;
    private final NLPProcessor nlpProcessor;

    public ModelController(ModelTrainingService modelTrainingService, NLPProcessor nlpProcessor) {
        this.modelTrainingService = modelTrainingService;
        this.nlpProcessor = nlpProcessor;
    }

    @GetMapping
    public ModelTrainingService.ModelStatus status() {
        return modelTrainingService.status();
    }

    @GetMapping("/metrics")
    public ModelEvaluator.Report metrics() {
        return modelTrainingService.evaluation();
    }

    @PostMapping("/retrain")
    public ModelTrainingService.ModelStatus retrain() {
        modelTrainingService.train();
        return modelTrainingService.status();
    }

    /** Dry run: the pipeline and the scores, with no answer and no history entry. */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> body) {
        return explain(body.getOrDefault("message", ""));
    }

    @GetMapping("/analyze")
    public Map<String, Object> analyzeQuery(@RequestParam("message") String message) {
        return explain(message);
    }

    private Map<String, Object> explain(String message) {
        NLPProcessor.Analysis analysis = nlpProcessor.analyze(message);

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("1_original", analysis.original());
        pipeline.put("2_normalized", analysis.normalized());
        pipeline.put("3_words", analysis.words());
        pipeline.put("4_features", analysis.features());
        pipeline.put("5_questionType", analysis.questionType().name());
        pipeline.put("6_sentiment", Map.of(
                "label", analysis.sentiment().sentiment().name(),
                "score", analysis.sentiment().score()));

        List<IntentClassifier.Prediction> predictions =
                modelTrainingService.predict(analysis.features());
        List<Map<String, Object>> scores = predictions.stream()
                .limit(5)
                .map(prediction -> Map.<String, Object>of(
                        "intent", prediction.intent(),
                        "confidence", Math.round(prediction.confidence() * 1000.0) / 1000.0))
                .toList();

        List<Map<String, Object>> neighbours = modelTrainingService
                .nearestExamples(analysis.features()).stream()
                .limit(5)
                .map(neighbour -> Map.<String, Object>of(
                        "example", neighbour.text(),
                        "intent", neighbour.intent(),
                        "similarity", Math.round(neighbour.similarity() * 1000.0) / 1000.0))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline);
        result.put("predictions", scores);
        result.put("nearestTrainingExamples", neighbours);
        result.put("model", modelTrainingService.model().name());
        return result;
    }

    /** The k nearest stored questions for a message, used by the debug view. */
    @GetMapping("/neighbours")
    public List<CosineSimilarityClassifier.Neighbour> neighbours(@RequestParam("message") String message) {
        return modelTrainingService.nearestExamples(nlpProcessor.extractFeatures(message))
                .stream().limit(10).toList();
    }
}
