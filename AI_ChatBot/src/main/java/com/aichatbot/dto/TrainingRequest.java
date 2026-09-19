package com.aichatbot.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Teaches the bot a new question for an intent that already exists. */
public record TrainingRequest(
        @NotBlank(message = "intent is required.") String intent,
        @NotBlank(message = "example is required.") String example) {

    /** Teaches the bot an entirely new FAQ: intent, examples and answers. */
    public record NewIntentRequest(
            @NotBlank(message = "name is required.") String name,
            String description,
            String category,
            List<String> examples,
            @NotBlank(message = "A primary answer is required.") String primaryAnswer,
            String detailAnswer,
            String exampleAnswer) {
    }
}
