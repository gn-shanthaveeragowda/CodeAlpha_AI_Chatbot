package com.aichatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A persisted turn of conversation: what the user asked, what the bot decided,
 * how sure it was and whether the user said the answer helped.
 *
 * <p>This table is both the chat history shown in the interface and the raw
 * material for the analytics dashboard and the feedback learning loop.</p>
 */
@Entity
@Table(name = "chat_history", indexes = {
        @Index(name = "idx_chat_session", columnList = "sessionId"),
        @Index(name = "idx_chat_created", columnList = "createdAt")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    @Column(length = 1000)
    private String userMessage;

    private String detectedIntent;

    @Column(length = 2000)
    private String botResponse;

    private double confidence;

    /** True when no intent cleared the confidence threshold. */
    private boolean fallback;

    private String sentiment;

    private double sentimentScore;

    /** Which engine produced the answer: RULE, MODEL, CONTEXT or FALLBACK. */
    private String strategy;

    private long responseTimeMs;

    /** Null until the user rates the answer, then true or false. */
    private Boolean helpful;

    private String correctedIntent;

    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(String sessionId, String userMessage, String detectedIntent, String botResponse,
                       double confidence, boolean fallback, String sentiment, double sentimentScore,
                       String strategy, long responseTimeMs) {
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.detectedIntent = detectedIntent;
        this.botResponse = botResponse;
        this.confidence = confidence;
        this.fallback = fallback;
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
        this.strategy = strategy;
        this.responseTimeMs = responseTimeMs;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getDetectedIntent() {
        return detectedIntent;
    }

    public String getBotResponse() {
        return botResponse;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getSentiment() {
        return sentiment;
    }

    public double getSentimentScore() {
        return sentimentScore;
    }

    public String getStrategy() {
        return strategy;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(Boolean helpful) {
        this.helpful = helpful;
    }

    public String getCorrectedIntent() {
        return correctedIntent;
    }

    public void setCorrectedIntent(String correctedIntent) {
        this.correctedIntent = correctedIntent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
