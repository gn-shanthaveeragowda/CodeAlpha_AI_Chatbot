package com.aichatbot.http;

import com.aichatbot.service.ModelTrainingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Liveness check that also reports whether the model finished training. */
@RestController
public class HealthController {

    private final ModelTrainingService modelTrainingService;

    public HealthController(ModelTrainingService modelTrainingService) {
        this.modelTrainingService = modelTrainingService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "ai-chatbot");
        health.put("modelTrained", modelTrainingService.isTrained());
        health.put("trainingExamples", modelTrainingService.status().trainingExamples());
        health.put("intents", modelTrainingService.status().intents());
        return health;
    }
}
