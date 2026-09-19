package com.aichatbot.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PorterStemmer")
class PorterStemmerTest {

    private final PorterStemmer stemmer = new PorterStemmer();

    @Test
    @DisplayName("reduces plural and -ing/-ed forms to a shared root")
    void reducesInflections() {
        assertEquals("invers", stemmer.stem("inversion"));
        assertEquals("run", stemmer.stem("running"));
        assertEquals("hope", stemmer.stem("hoped"));
        assertEquals("fall", stemmer.stem("falling"));
        assertEquals("cat", stemmer.stem("cats"));
    }

    @Test
    @DisplayName("collapses variations of 'inherit' to one stem so classifier features match")
    void collapsesInheritanceFamily() {
        String base = stemmer.stem("inherit");
        assertEquals(base, stemmer.stem("inherits"));
        assertEquals(base, stemmer.stem("inheriting"));
        assertEquals(base, stemmer.stem("inherited"));
    }

    @Test
    @DisplayName("leaves short words untouched")
    void leavesShortWordsAlone() {
        assertEquals("is", stemmer.stem("is"));
        assertEquals("a", stemmer.stem("a"));
        assertEquals("", stemmer.stem(""));
    }

    @Test
    @DisplayName("handles null gracefully")
    void handlesNull() {
        assertEquals("", stemmer.stem(null));
    }

    @Test
    @DisplayName("stems -ational and -ion suffixes (step 2 / step 4 rules)")
    void stemsCommonSuffixes() {
        assertEquals("nation", stemmer.stem("national"));
        assertEquals("relat", stemmer.stem("relational"));
        assertEquals("connect", stemmer.stem("connection"));
    }

    @Test
    @DisplayName("collapses domain vocabulary families to shared stems")
    void collapsesDomainVocabulary() {
        assertEquals(stemmer.stem("polymorphism"), stemmer.stem("polymorphic"));
        assertEquals("oper", stemmer.stem("operator"));
        assertEquals("oper", stemmer.stem("operation"));
    }
}
