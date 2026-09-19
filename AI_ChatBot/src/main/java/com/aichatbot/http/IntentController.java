package com.aichatbot.http;

import com.aichatbot.entity.Intent;
import com.aichatbot.entity.Response;
import com.aichatbot.repository.IntentRepository;
import com.aichatbot.repository.ResponseRepository;
import com.aichatbot.repository.TrainingExampleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Browsable view of everything the bot has been trained to answer. */
@RestController
@RequestMapping("/api/intents")
public class IntentController {

    private final IntentRepository intentRepository;
    private final TrainingExampleRepository trainingExampleRepository;
    private final ResponseRepository responseRepository;

    public IntentController(IntentRepository intentRepository,
                            TrainingExampleRepository trainingExampleRepository,
                            ResponseRepository responseRepository) {
        this.intentRepository = intentRepository;
        this.trainingExampleRepository = trainingExampleRepository;
        this.responseRepository = responseRepository;
    }

    /** One intent with the size of its training data. */
    public record IntentSummary(String name, String description, String category,
                                long exampleCount, long answerCount) {
    }

    @GetMapping
    public List<IntentSummary> all() {
        List<IntentSummary> summaries = new ArrayList<>();
        for (Intent intent : intentRepository.findAllByOrderByCategoryAscNameAsc()) {
            summaries.add(new IntentSummary(
                    intent.getName(),
                    intent.getDescription(),
                    intent.getCategory(),
                    trainingExampleRepository.countByIntentName(intent.getName()),
                    responseRepository.countByIntentName(intent.getName())));
        }
        return summaries;
    }

    /** Intents grouped by category, which is how the topic browser renders them. */
    @GetMapping("/by-category")
    public Map<String, List<IntentSummary>> byCategory() {
        Map<String, List<IntentSummary>> grouped = new LinkedHashMap<>();
        for (IntentSummary summary : all()) {
            String category = summary.category() == null ? "Other" : summary.category();
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(summary);
        }
        return grouped;
    }

    /** The training examples and stored answers behind one intent. */
    @GetMapping("/{name}")
    public Map<String, Object> detail(@PathVariable String name) {
        Map<String, Object> detail = new LinkedHashMap<>();
        intentRepository.findByName(name).ifPresent(intent -> {
            detail.put("name", intent.getName());
            detail.put("description", intent.getDescription());
            detail.put("category", intent.getCategory());
        });
        detail.put("examples", trainingExampleRepository.findByIntentName(name).stream()
                .map(example -> Map.of(
                        "text", example.getExampleText(),
                        "source", String.valueOf(example.getSource())))
                .toList());
        detail.put("answers", responseRepository.findByIntentName(name).stream()
                .map(response -> Map.of(
                        "type", response.getResponseType().name(),
                        "text", response.getResponseText()))
                .toList());
        return detail;
    }

    /** Names only, used to populate the correction dropdown in the interface. */
    @GetMapping("/names")
    public List<String> names() {
        return intentRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(Intent::getName)
                .toList();
    }

    /** Count of stored answers by type, a quick view of knowledge base depth. */
    @GetMapping("/coverage")
    public Map<String, Long> coverage() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Response.ResponseType type : Response.ResponseType.values()) {
            counts.put(type.name(), responseRepository.findAll().stream()
                    .filter(response -> response.getResponseType() == type)
                    .count());
        }
        return counts;
    }
}
