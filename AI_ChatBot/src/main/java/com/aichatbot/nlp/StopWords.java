package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Stop word filter for the tokenizer.
 *
 * <p>The list deliberately keeps question words such as "what", "how" and "why"
 * out of the removal set when they carry intent: "how" distinguishes
 * "how do I create a method" from "what is a method", and the follow-up
 * detector relies on words like "more" and "example".</p>
 */
@Component
public class StopWords {

    private static final Set<String> WORDS = Set.of(
            "a", "an", "the", "is", "are", "am", "was", "were", "be", "been", "being",
            "do", "does", "did", "of", "to", "in", "on", "at", "by", "for", "with",
            "and", "or", "but", "if", "so", "as", "that", "this", "these", "those",
            "i", "me", "my", "you", "your", "it", "its", "we", "us", "they", "them",
            "please", "kind", "kindly", "just", "some", "any", "there", "here",
            "would", "could", "should", "shall", "will", "may", "might", "must",
            "have", "has", "had", "get", "got", "know", "want", "need", "like",
            // Question words are removed too. They are the most frequent tokens
            // in the corpus and swamped the classifier: "what is git" was
            // matching "what are you" on the strength of "what" alone. The
            // question form is still captured separately by the question type
            // detector, which reads the words before this filter runs.
            "what", "how", "why", "when", "where", "which", "who", "whom",
            "can", "tell", "explain", "describe", "give", "show", "say"
    );

    /** Words that look like stop words but change meaning in this domain. */
    private static final Set<String> PROTECTED = Set.of(
            "not", "no", "more", "example", "between", "difference",
            "versus", "vs", "use", "using", "work", "works"
    );

    public boolean isStopWord(String token) {
        return !PROTECTED.contains(token) && WORDS.contains(token);
    }

    public Set<String> all() {
        return WORDS;
    }
}
