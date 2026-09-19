package com.aichatbot.http;

import com.aichatbot.dto.FeedbackRequest;
import com.aichatbot.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives thumbs up and thumbs down ratings. A correction here retrains the
 * model immediately, which is the online learning loop.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final LearningService learningService;

    public FeedbackController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public LearningService.LearningResult submit(@Valid @RequestBody FeedbackRequest request) {
        return learningService.recordFeedback(request);
    }
}
