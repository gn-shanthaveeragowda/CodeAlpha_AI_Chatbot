package com.aichatbot.http;

import com.aichatbot.dto.TrainingRequest;
import com.aichatbot.entity.UnansweredQuestion;
import com.aichatbot.repository.UnansweredQuestionRepository;
import com.aichatbot.service.DataInitializer;
import com.aichatbot.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Teaching endpoints: add an example to an existing FAQ, create a whole new
 * FAQ, reload the dataset file, or read the queue of unanswered questions.
 */
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final LearningService learningService;
    private final DataInitializer dataInitializer;
    private final UnansweredQuestionRepository unansweredQuestionRepository;

    public TrainingController(LearningService learningService,
                              DataInitializer dataInitializer,
                              UnansweredQuestionRepository unansweredQuestionRepository) {
        this.learningService = learningService;
        this.dataInitializer = dataInitializer;
        this.unansweredQuestionRepository = unansweredQuestionRepository;
    }

    @PostMapping("/examples")
    public LearningService.LearningResult addExample(@Valid @RequestBody TrainingRequest request) {
        return learningService.teachExample(request);
    }

    @PostMapping("/intents")
    public LearningService.LearningResult addIntent(
            @Valid @RequestBody TrainingRequest.NewIntentRequest request) {
        return learningService.teachIntent(request);
    }

    /** Re-reads faq-dataset.json, picking up any FAQs added to the file. */
    @PostMapping("/reload-dataset")
    public DataInitializer.SeedResult reloadDataset() {
        return dataInitializer.seed();
    }

    @GetMapping("/gaps")
    public List<UnansweredQuestion> trainingGaps() {
        return unansweredQuestionRepository
                .findTop20ByResolvedFalseOrderByTimesAskedDescLastAskedAtDesc();
    }
}
