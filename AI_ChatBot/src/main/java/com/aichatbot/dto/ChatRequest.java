package com.aichatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An incoming user message. The session id lets the bot keep conversational
 * context, so follow-ups such as "tell me more" resolve to the right topic.
 */
public record ChatRequest(
        @NotBlank(message = "Please enter a message.")
        @Size(max = 500, message = "Please keep your message under 500 characters.")
        String message,

        String sessionId) {

    public String sessionOrDefault() {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId;
    }
}
