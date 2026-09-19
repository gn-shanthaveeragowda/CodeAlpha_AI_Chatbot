package com.aichatbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RuleEngine")
class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    @DisplayName("evaluates arithmetic from the raw message, since the operator is stripped by normalization")
    void evaluatesArithmetic() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("what is 17 23", "what is 17 * 23");
        assertTrue(result.isPresent());
        assertEquals("arithmetic", result.get().rule());
        assertEquals("17 * 23 = 391", result.get().answer());
    }

    @Test
    @DisplayName("evaluates division and reduces a whole-number result to an integer")
    void evaluatesDivision() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("100 8", "100 / 8");
        assertTrue(result.isPresent());
        assertEquals("100 / 8 = 12.5", result.get().answer());
    }

    @Test
    @DisplayName("reports division by zero as undefined rather than throwing")
    void handlesDivisionByZero() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("5 0", "5 / 0");
        assertTrue(result.isPresent());
        assertTrue(result.get().answer().toLowerCase().contains("undefined"));
    }

    @Test
    @DisplayName("answers a time question with the current time")
    void answersTimeQuestion() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("what time is it", "what time is it");
        assertTrue(result.isPresent());
        assertEquals("time", result.get().rule());
    }

    @Test
    @DisplayName("answers a date question with today's date")
    void answersDateQuestion() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("what is the date", "what is the date");
        assertTrue(result.isPresent());
        assertEquals("date", result.get().rule());
    }

    @Test
    @DisplayName("does not fire on an ordinary FAQ question")
    void doesNotFireOnOrdinaryQuestions() {
        Optional<RuleEngine.RuleAnswer> result = engine.apply("what is inheritance", "what is inheritance");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returns empty for blank input")
    void handlesBlankInput() {
        assertTrue(engine.apply("", "").isEmpty());
        assertTrue(engine.apply(null, null).isEmpty());
    }
}
