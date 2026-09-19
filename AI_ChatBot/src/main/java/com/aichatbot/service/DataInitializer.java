package com.aichatbot.service;

import com.aichatbot.entity.Intent;
import com.aichatbot.entity.Response;
import com.aichatbot.entity.TrainingExample;
import com.aichatbot.repository.IntentRepository;
import com.aichatbot.repository.ResponseRepository;
import com.aichatbot.repository.TrainingExampleRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads the FAQ knowledge base from {@code faq-dataset.json} into the database.
 *
 * <p>Keeping the training data in a resource file rather than in Java source
 * means a new FAQ can be added by editing JSON, with no recompilation and no
 * risk of breaking the code. The load is idempotent: existing intents,
 * examples and answers are skipped, so restarting never duplicates rows and
 * anything the bot learned at run time survives.</p>
 */
@Service
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DATASET = "faq-dataset.json";

    private final IntentRepository intentRepository;
    private final TrainingExampleRepository trainingExampleRepository;
    private final ResponseRepository responseRepository;
    private final ObjectMapper objectMapper;

    public DataInitializer(IntentRepository intentRepository,
                           TrainingExampleRepository trainingExampleRepository,
                           ResponseRepository responseRepository,
                           ObjectMapper objectMapper) {
        this.intentRepository = intentRepository;
        this.trainingExampleRepository = trainingExampleRepository;
        this.responseRepository = responseRepository;
        this.objectMapper = objectMapper;
    }

    /** JSON shape of the dataset file. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dataset(String version, String description, List<IntentDefinition> intents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntentDefinition(String name,
                                   String description,
                                   String category,
                                   List<String> examples,
                                   Map<String, List<String>> responses) {
    }

    /** Result of a seeding run, logged at startup and returned by the admin API. */
    public record SeedResult(int intentsCreated, int examplesCreated, int responsesCreated) {
    }

    @Transactional
    public SeedResult seed() {
        Dataset dataset = readDataset();
        if (dataset == null || dataset.intents() == null) {
            log.warn("No FAQ dataset found on the classpath; the bot will start untrained.");
            return new SeedResult(0, 0, 0);
        }

        int intentsCreated = 0;
        int examplesCreated = 0;
        int responsesCreated = 0;

        for (IntentDefinition definition : dataset.intents()) {
            if (definition.name() == null || definition.name().isBlank()) {
                continue;
            }

            Intent intent = intentRepository.findByName(definition.name()).orElse(null);
            if (intent == null) {
                intent = intentRepository.save(new Intent(
                        definition.name(), definition.description(), definition.category()));
                intentsCreated++;
            }

            examplesCreated += seedExamples(definition, intent);
            responsesCreated += seedResponses(definition, intent);
        }

        log.info("FAQ dataset {} loaded: {} new intents, {} new examples, {} new answers",
                dataset.version(), intentsCreated, examplesCreated, responsesCreated);
        return new SeedResult(intentsCreated, examplesCreated, responsesCreated);
    }

    private int seedExamples(IntentDefinition definition, Intent intent) {
        if (definition.examples() == null) {
            return 0;
        }
        int created = 0;
        for (String example : definition.examples()) {
            if (example == null || example.isBlank()) {
                continue;
            }
            String trimmed = example.trim();
            if (!trainingExampleRepository
                    .existsByExampleTextIgnoreCaseAndIntent_Name(trimmed, intent.getName())) {
                trainingExampleRepository.save(
                        new TrainingExample(trimmed, intent, TrainingExample.Source.SEED));
                created++;
            }
        }
        return created;
    }

    private int seedResponses(IntentDefinition definition, Intent intent) {
        if (definition.responses() == null) {
            return 0;
        }
        int created = 0;
        for (Map.Entry<String, List<String>> entry : definition.responses().entrySet()) {
            Response.ResponseType type = parseType(entry.getKey());
            if (type == null || entry.getValue() == null) {
                continue;
            }
            for (String text : entry.getValue()) {
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (!responseRepository.existsByIntentNameAndResponseTypeAndResponseText(
                        intent.getName(), type, text)) {
                    responseRepository.save(new Response(text, type, intent));
                    created++;
                }
            }
        }
        return created;
    }

    private Response.ResponseType parseType(String raw) {
        try {
            return Response.ResponseType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unknown response type '{}' in the FAQ dataset; skipping.", raw);
            return null;
        }
    }

    private Dataset readDataset() {
        ClassPathResource resource = new ClassPathResource(DATASET);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, Dataset.class);
        } catch (Exception e) {
            log.error("Could not read {}: {}", DATASET, e.getMessage());
            return null;
        }
    }
}
