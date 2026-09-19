package com.aichatbot.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SentimentAnalyzer")
class SentimentAnalyzerTest {

    private final SentimentAnalyzer analyzer = new SentimentAnalyzer();

    @Test
    @DisplayName("detects positive sentiment from positive words")
    void detectsPositive() {
        SentimentAnalyzer.Result result = analyzer.analyze(List.of("this", "is", "great", "and", "helpful"));
        assertEquals(SentimentAnalyzer.Sentiment.POSITIVE, result.sentiment());
        assertTrue(result.isPositive());
    }

    @Test
    @DisplayName("detects negative sentiment from negative words")
    void detectsNegative() {
        SentimentAnalyzer.Result result = analyzer.analyze(List.of("this", "is", "confusing", "and", "frustrating"));
        assertEquals(SentimentAnalyzer.Sentiment.NEGATIVE, result.sentiment());
        assertTrue(result.isNegative());
    }

    @Test
    @DisplayName("treats factual questions with no sentiment words as neutral")
    void detectsNeutral() {
        SentimentAnalyzer.Result result = analyzer.analyze(List.of("what", "is", "java"));
        assertEquals(SentimentAnalyzer.Sentiment.NEUTRAL, result.sentiment());
        assertEquals(0.0, result.score(), 0.001);
    }

    @Test
    @DisplayName("negation flips polarity: 'not bad' reads positive")
    void negationFlipsPolarity() {
        SentimentAnalyzer.Result result = analyzer.analyze(List.of("this", "is", "not", "bad"));
        assertTrue(result.score() > 0, "expected a positive score for 'not bad' but got " + result.score());
    }

    @Test
    @DisplayName("handles an empty token list without error")
    void handlesEmptyInput() {
        SentimentAnalyzer.Result result = analyzer.analyze(List.of());
        assertEquals(SentimentAnalyzer.Sentiment.NEUTRAL, result.sentiment());
    }
}
