package com.aichatbot.nlp;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The full natural language processing pipeline.
 *
 * <pre>
 *   raw text
 *     -> normalization   (case, accents, contractions, shorthand, punctuation)
 *     -> tokenization    (whitespace split)
 *     -> spell check     (Levenshtein against the trained vocabulary)
 *     -> synonym mapping (oops -> oop, db -> database)
 *     -> stop removal    (keeping question words)
 *     -> stemming        (Porter algorithm)
 *     -> n-grams         (unigrams plus bigrams)
 * </pre>
 *
 * <p>The resulting feature list is what both classifiers consume, so training
 * examples and live user messages are always processed identically.</p>
 */
@Service
public class NLPProcessor {

    private final TextNormalizer normalizer;
    private final StopWords stopWords;
    private final SynonymDictionary synonyms;
    private final SpellCorrector spellCorrector;
    private final PorterStemmer stemmer;
    private final SentimentAnalyzer sentimentAnalyzer;

    public NLPProcessor(TextNormalizer normalizer,
                        StopWords stopWords,
                        SynonymDictionary synonyms,
                        SpellCorrector spellCorrector,
                        PorterStemmer stemmer,
                        SentimentAnalyzer sentimentAnalyzer) {
        this.normalizer = normalizer;
        this.stopWords = stopWords;
        this.synonyms = synonyms;
        this.spellCorrector = spellCorrector;
        this.stemmer = stemmer;
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    /** The question form of an utterance, used when choosing a response style. */
    public enum QuestionType { DEFINITION, PROCEDURE, REASON, COMPARISON, EXAMPLE, YES_NO, STATEMENT }

    /**
     * The complete analysis of one utterance.
     *
     * @param original    text exactly as the user typed it
     * @param normalized  cleaned lowercase text
     * @param words       normalized words before stop word removal and stemming
     * @param features    stems plus bigrams, the input to the classifiers
     * @param sentiment   polarity of the message
     * @param questionType the grammatical shape of the question
     */
    public record Analysis(String original,
                           String normalized,
                           List<String> words,
                           List<String> features,
                           SentimentAnalyzer.Result sentiment,
                           QuestionType questionType) {

        public boolean isEmpty() {
            return features.isEmpty();
        }

        public Set<String> featureSet() {
            return new LinkedHashSet<>(features);
        }
    }

    /** Runs the whole pipeline over one utterance. */
    public Analysis analyze(String raw) {
        String normalized = normalizer.normalize(raw);
        if (normalized.isEmpty()) {
            return new Analysis(raw == null ? "" : raw, "", List.of(), List.of(),
                    new SentimentAnalyzer.Result(SentimentAnalyzer.Sentiment.NEUTRAL, 0.0),
                    QuestionType.STATEMENT);
        }

        List<String> words = List.of(normalized.split(" "));
        List<String> stems = extractStems(words, true);
        if (stems.isEmpty()) {
            // Messages made entirely of stop words, such as "how are you" or
            // "what can you do", would otherwise produce no features at all.
            // Keeping every word is better than having nothing to classify.
            stems = extractStems(words, false);
        }
        List<String> features = withBigrams(stems);

        return new Analysis(
                raw,
                normalized,
                words,
                features,
                sentimentAnalyzer.analyze(words),
                detectQuestionType(normalized, words));
    }

    /** Convenience method for callers that only need the classifier features. */
    public List<String> extractFeatures(String raw) {
        return analyze(raw).features();
    }

    /**
     * Spell check, canonicalize, drop stop words, then stem. Order matters:
     * correction runs against real vocabulary, and stemming runs last so the
     * synonym table stays readable.
     */
    private List<String> extractStems(List<String> words, boolean removeStopWords) {
        List<String> stems = new ArrayList<>(words.size());
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String token = synonyms.isKnown(word) ? word : spellCorrector.correct(word);
            token = synonyms.canonicalize(token);
            if (removeStopWords && stopWords.isStopWord(token)) {
                continue;
            }
            String stem = stemmer.stem(token);
            if (!stem.isBlank()) {
                stems.add(stem);
            }
        }
        return stems;
    }

    /**
     * Adds bigrams so word order carries some weight. Without them
     * "method overloading" and "overloading method" look identical, and
     * "difference between class and object" loses its pairing.
     */
    private List<String> withBigrams(List<String> stems) {
        List<String> features = new ArrayList<>(stems);
        for (int i = 0; i + 1 < stems.size(); i++) {
            features.add(stems.get(i) + "_" + stems.get(i + 1));
        }
        return features;
    }

    /** Vocabulary of stems only, used when building the spell check dictionary. */
    public Set<String> vocabularyOf(String raw) {
        String normalized = normalizer.normalize(raw);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        Set<String> vocabulary = new LinkedHashSet<>();
        for (String word : normalized.split(" ")) {
            if (word.length() >= 3) {
                vocabulary.add(synonyms.canonicalize(word));
            }
        }
        return vocabulary;
    }

    private QuestionType detectQuestionType(String normalized, List<String> words) {
        if (normalized.contains("difference") || normalized.contains(" vs ")
                || normalized.contains("versus") || normalized.contains("compare")) {
            return QuestionType.COMPARISON;
        }
        if (normalized.contains("example") || normalized.contains("sample")
                || normalized.contains("demo")) {
            return QuestionType.EXAMPLE;
        }
        if (words.isEmpty()) {
            return QuestionType.STATEMENT;
        }
        String first = words.get(0);
        return switch (first) {
            case "what", "define", "meaning", "explain", "describe", "who" -> QuestionType.DEFINITION;
            case "how" -> QuestionType.PROCEDURE;
            case "why" -> QuestionType.REASON;
            case "which", "when", "where" -> QuestionType.COMPARISON;
            case "is", "are", "can", "do", "does", "should", "will" -> QuestionType.YES_NO;
            default -> QuestionType.STATEMENT;
        };
    }
}
