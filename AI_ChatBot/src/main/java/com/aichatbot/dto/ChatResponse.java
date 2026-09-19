package com.aichatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The full result of one chatbot turn.
 *
 * <p>Beyond the answer itself it carries the reasoning: which intent won, how
 * confident the model was, which engine answered, what the NLP pipeline
 * extracted and which other intents were close. The web interface renders all
 * of it, so the bot's decision is inspectable rather than a black box.</p>
 */
public record ChatResponse(
        Long messageId,
        String sessionId,
        String message,
        String intent,
        String intentDescription,
        String response,
        double confidence,
        boolean fallback,
        String strategy,
        String sentiment,
        double sentimentScore,
        String questionType,
        List<String> tokens,
        String matchedExample,
        List<IntentScore> alternatives,
        List<String> suggestions,
        long responseTimeMs,
        LocalDateTime timestamp) {

    /** A runner-up intent, shown as a "did you mean" option. */
    public record IntentScore(String intent, double confidence) {
    }
}
