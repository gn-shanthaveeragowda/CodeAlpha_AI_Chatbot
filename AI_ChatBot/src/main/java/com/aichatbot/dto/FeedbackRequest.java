package com.aichatbot.dto;

import jakarta.validation.constraints.NotNull;

/**
 * User rating of a single answer. A negative rating with a corrected intent
 * teaches the bot: the original question is stored as a training example for
 * the right intent and the model is retrained.
 */
public record FeedbackRequest(
        @NotNull(message = "messageId is required.")
        Long messageId,

        @NotNull(message = "Please say whether the answer helped.")
        Boolean helpful,

        String correctedIntent) {
}
