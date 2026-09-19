package com.aichatbot.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NLPProcessor pipeline")
class NLPProcessorTest {

    private final NLPProcessor nlp = new NLPProcessor(
            new TextNormalizer(), new StopWords(), new SynonymDictionary(),
            new SpellCorrector(), new PorterStemmer(), new SentimentAnalyzer());

    @Test
    @DisplayName("extracts stems and a bigram from a definition question")
    void extractsFeaturesFromDefinitionQuestion() {
        NLPProcessor.Analysis analysis = nlp.analyze("What is inheritance in java?");
        assertEquals(java.util.List.of("inherit", "java", "inherit_java"), analysis.features());
        assertEquals(NLPProcessor.QuestionType.DEFINITION, analysis.questionType());
    }

    @Test
    @DisplayName("keeps every word when stop-word removal would empty the feature list")
    void fallsBackWhenAllWordsAreStopWords() {
        NLPProcessor.Analysis analysis = nlp.analyze("how are you");
        assertTrue(!analysis.isEmpty(), "a short greeting should still produce features");
        assertEquals(NLPProcessor.QuestionType.PROCEDURE, analysis.questionType());
    }

    @Test
    @DisplayName("recognises an example request as its own question type")
    void detectsExampleQuestionType() {
        NLPProcessor.Analysis analysis = nlp.analyze("show me an example");
        assertEquals(NLPProcessor.QuestionType.EXAMPLE, analysis.questionType());
    }

    @Test
    @DisplayName("recognises a comparison question and keeps both compared terms as features")
    void detectsComparisonQuestionType() {
        NLPProcessor.Analysis analysis = nlp.analyze("difference between class and object");
        assertEquals(NLPProcessor.QuestionType.COMPARISON, analysis.questionType());
        assertTrue(analysis.features().contains("class"));
        assertTrue(analysis.features().contains("object"));
    }

    @Test
    @DisplayName("canonicalises domain synonyms so 'oops' reaches the same features as 'oop'")
    void canonicalisesSynonyms() {
        NLPProcessor.Analysis analysis = nlp.analyze("explain oops concepts");
        assertTrue(analysis.features().contains("oop"), "expected 'oops' to canonicalise to 'oop'");
    }

    @Test
    @DisplayName("treats blank input as empty rather than throwing")
    void handlesBlankInput() {
        NLPProcessor.Analysis analysis = nlp.analyze("");
        assertTrue(analysis.isEmpty());
        assertEquals(java.util.List.of(), analysis.features());
    }
}
