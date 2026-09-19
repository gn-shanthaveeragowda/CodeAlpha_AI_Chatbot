package com.aichatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A labelled question used to train the classifiers.
 *
 * <p>The source field records where the example came from. Anything marked
 * LEARNED was added at runtime from a real conversation the user confirmed was
 * answered correctly, which is how the bot improves after deployment.</p>
 */
@Entity
@Table(name = "training_examples")
public class TrainingExample {

    public enum Source { SEED, LEARNED, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String exampleText;

    @Enumerated(EnumType.STRING)
    private Source source;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false)
    private Intent intent;

    protected TrainingExample() {
    }

    public TrainingExample(String exampleText, Intent intent, Source source) {
        this.exampleText = exampleText;
        this.intent = intent;
        this.source = source;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getExampleText() {
        return exampleText;
    }

    public Source getSource() {
        return source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Intent getIntent() {
        return intent;
    }
}
