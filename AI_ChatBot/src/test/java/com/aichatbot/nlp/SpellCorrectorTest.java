package com.aichatbot.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SpellCorrector")
class SpellCorrectorTest {

    private final SpellCorrector corrector = new SpellCorrector();

    @BeforeEach
    void loadVocabulary() {
        corrector.loadVocabulary(Set.of(
                "inheritance", "polymorphism", "encapsulation", "abstraction",
                "database", "java", "python"));
    }

    @Test
    @DisplayName("corrects a one-edit typo to the nearest known word")
    void correctsOneEditTypo() {
        assertEquals("inheritance", corrector.correct("inheritence"));
        assertEquals("database", corrector.correct("datbase"));
    }

    @Test
    @DisplayName("corrects a two-edit typo on a long word")
    void correctsTwoEditTypoOnLongWord() {
        assertEquals("polymorphism", corrector.correct("polymorphisim"));
    }

    @Test
    @DisplayName("leaves an already-known word unchanged")
    void leavesKnownWordUnchanged() {
        assertEquals("java", corrector.correct("java"));
    }

    @Test
    @DisplayName("leaves a word with no close match unchanged rather than guessing wildly")
    void leavesUnrelatedWordUnchanged() {
        assertEquals("xyz", corrector.correct("xyz"));
    }

    @Test
    @DisplayName("does not touch very short words, where one edit changes meaning entirely")
    void leavesShortWordsAlone() {
        SpellCorrector fresh = new SpellCorrector();
        fresh.loadVocabulary(Set.of("are", "you"));
        assertEquals("or", fresh.correct("or"));
    }

    @Test
    @DisplayName("computes Levenshtein distance correctly")
    void computesEditDistance() {
        assertEquals(0, corrector.levenshtein("java", "java", 5));
        assertEquals(1, corrector.levenshtein("java", "javas", 5));
        assertEquals(3, corrector.levenshtein("kitten", "sitting", 5));
    }
}
