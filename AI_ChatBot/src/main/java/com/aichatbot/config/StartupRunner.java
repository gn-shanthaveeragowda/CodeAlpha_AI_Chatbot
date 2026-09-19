package com.aichatbot.config;

import com.aichatbot.service.DataInitializer;
import com.aichatbot.service.ModelTrainingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Brings the chatbot up in the right order: load the FAQ knowledge base into
 * the database first, then train the classifier on whatever is in there.
 *
 * <p>Training must come second because it reads from the database, which means
 * it automatically picks up anything the bot learned during previous runs as
 * well as the seeded dataset.</p>
 */
@Component
@Order(1)
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final DataInitializer dataInitializer;
    private final ModelTrainingService modelTrainingService;

    public StartupRunner(DataInitializer dataInitializer, ModelTrainingService modelTrainingService) {
        this.dataInitializer = dataInitializer;
        this.modelTrainingService = modelTrainingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        dataInitializer.seed();
        modelTrainingService.train();
        ModelTrainingService.ModelStatus status = modelTrainingService.status();
        log.info("CodeMate is ready: {} intents, {} training examples, {} features",
                status.intents(), status.trainingExamples(), status.featureVocabulary());
    }
}
