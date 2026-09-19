package com.aichatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A question the chatbot could not answer confidently.
 *
 * <p>Repeated entries here are the queue of FAQs worth teaching next, so the
 * training gap is visible instead of silently disappearing into the logs.</p>
 */
@Entity
@Table(name = "unanswered_questions")
public class UnansweredQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000, nullable = false)
    private String question;

    /** Normalized form, used to group the same question asked different ways. */
    @Column(length = 1000)
    private String normalizedQuestion;

    private int timesAsked;

    private double bestConfidence;

    private String closestIntent;

    private boolean resolved;

    private LocalDateTime firstAskedAt;

    private LocalDateTime lastAskedAt;

    protected UnansweredQuestion() {
    }

    public UnansweredQuestion(String question, String normalizedQuestion,
                              double bestConfidence, String closestIntent) {
        this.question = question;
        this.normalizedQuestion = normalizedQuestion;
        this.bestConfidence = bestConfidence;
        this.closestIntent = closestIntent;
        this.timesAsked = 1;
        this.resolved = false;
        this.firstAskedAt = LocalDateTime.now();
        this.lastAskedAt = this.firstAskedAt;
    }

    /** Called when the same unanswered question comes in again. */
    public void recordRepeat(double confidence, String closestIntent) {
        this.timesAsked++;
        this.lastAskedAt = LocalDateTime.now();
        if (confidence > this.bestConfidence) {
            this.bestConfidence = confidence;
            this.closestIntent = closestIntent;
        }
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    public int getTimesAsked() {
        return timesAsked;
    }

    public double getBestConfidence() {
        return bestConfidence;
    }

    public String getClosestIntent() {
        return closestIntent;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public LocalDateTime getFirstAskedAt() {
        return firstAskedAt;
    }

    public LocalDateTime getLastAskedAt() {
        return lastAskedAt;
    }
}
