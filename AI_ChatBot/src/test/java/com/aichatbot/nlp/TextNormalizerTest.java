package com.aichatbot.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("TextNormalizer")
class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    @DisplayName("expands contractions before stripping punctuation")
    void expandsContractions() {
        assertEquals("what is oop", normalizer.normalize("What's OOP???"));
        assertEquals("i do not understand", normalizer.normalize("I dont understand"));
    }

    @Test
    @DisplayName("expands common chat shorthand")
    void expandsShorthand() {
        assertEquals("you are helpful", normalizer.normalize("u r helpful"));
    }

    @Test
    @DisplayName("collapses elongated characters without destroying the word")
    void collapsesElongation() {
        assertEquals("heelp me", normalizer.normalize("heeeelp me"));
    }

    @Test
    @DisplayName("keeps + and # so language names such as c++ and c# survive")
    void keepsLanguageSymbols() {
        assertEquals("c++ and c# are languages", normalizer.normalize("C++ and C# are languages"));
    }

    @Test
    @DisplayName("strips accents to plain ASCII")
    void stripsAccents() {
        assertEquals("cafe uber naive", normalizer.normalize("Café über naïve"));
    }

    @Test
    @DisplayName("collapses repeated whitespace")
    void collapsesWhitespace() {
        assertEquals("multiple spaces", normalizer.normalize("  multiple   spaces  "));
    }

    @Test
    @DisplayName("returns empty string for blank or null input")
    void handlesBlankInput() {
        assertEquals("", normalizer.normalize(""));
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("   "));
    }
}
