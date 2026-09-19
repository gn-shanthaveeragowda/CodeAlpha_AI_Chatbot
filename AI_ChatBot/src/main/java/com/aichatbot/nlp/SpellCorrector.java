package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Corrects typos by matching unknown tokens against the vocabulary learned from
 * the training corpus, using Levenshtein edit distance.
 *
 * <p>"inheritence", "polymorphisim" and "datbase" all reach the right intent
 * because of this stage. The vocabulary is rebuilt every time the model is
 * retrained, so newly taught words become correction targets immediately.</p>
 */
@Component
public class SpellCorrector {

    private static final int SHORT_WORD_LIMIT = 4;
    private static final int LONG_WORD_LIMIT = 7;

    private volatile Set<String> vocabulary = Set.of();
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** Replaces the correction vocabulary. Called by the training service. */
    public void loadVocabulary(Set<String> words) {
        this.vocabulary = Collections.unmodifiableSet(new HashSet<>(words));
        this.cache.clear();
    }

    /**
     * Returns the closest known word, or the original token when it is already
     * known, too short to correct safely, or has no close match.
     */
    public String correct(String token) {
        if (token == null || token.length() < SHORT_WORD_LIMIT || vocabulary.isEmpty()) {
            return token;
        }
        if (vocabulary.contains(token)) {
            return token;
        }
        return cache.computeIfAbsent(token, this::findClosest);
    }

    private String findClosest(String token) {
        // Allow a second edit only for longer words, where one typo in ten
        // characters is common but a two-edit match is still unambiguous.
        int maxDistance = token.length() >= LONG_WORD_LIMIT ? 2 : 1;
        String best = token;
        int bestDistance = maxDistance + 1;

        for (String candidate : vocabulary) {
            // Length difference alone already exceeds the budget: skip early.
            if (Math.abs(candidate.length() - token.length()) > maxDistance) {
                continue;
            }
            int distance = levenshtein(token, candidate, maxDistance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
                if (distance == 1) {
                    break;
                }
            }
        }
        return bestDistance <= maxDistance ? best : token;
    }

    /**
     * Edit distance with an early exit once every cell in a row exceeds the
     * limit, which keeps lookups fast over a large vocabulary.
     */
    public int levenshtein(String a, String b, int limit) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            current[0] = i;
            int rowMinimum = current[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                rowMinimum = Math.min(rowMinimum, current[j]);
            }
            if (rowMinimum > limit) {
                return limit + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    public int vocabularySize() {
        return vocabulary.size();
    }
}
