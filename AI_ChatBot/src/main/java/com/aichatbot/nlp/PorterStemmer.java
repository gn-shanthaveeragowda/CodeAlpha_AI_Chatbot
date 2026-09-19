package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of the Porter stemming algorithm (Porter, 1980).
 *
 * <p>Stemming reduces inflected words to a common root so that "running", "runs"
 * and "ran" style variations collapse onto the same feature. This lets the
 * classifier match "explain inheritance in java" against the trained example
 * "inheritance explained" even though no word matches literally.</p>
 *
 * <p>Written from scratch so the project has no external NLP dependency.</p>
 */
@Component
public final class PorterStemmer {

    /** Suffix pairs for step 2, ordered longest-first so specific rules win. */
    private static final List<String[]> STEP2 = List.of(
            new String[]{"ization", "ize"}, new String[]{"iveness", "ive"},
            new String[]{"fulness", "ful"}, new String[]{"ousness", "ous"},
            new String[]{"ational", "ate"}, new String[]{"tional", "tion"},
            new String[]{"biliti", "ble"}, new String[]{"entli", "ent"},
            new String[]{"ousli", "ous"}, new String[]{"iviti", "ive"},
            new String[]{"alism", "al"}, new String[]{"ation", "ate"},
            new String[]{"aliti", "al"}, new String[]{"enci", "ence"},
            new String[]{"anci", "ance"}, new String[]{"izer", "ize"},
            new String[]{"abli", "able"}, new String[]{"alli", "al"},
            new String[]{"ator", "ate"}, new String[]{"logi", "log"},
            new String[]{"eli", "e"}
    );

    private static final List<String[]> STEP3 = List.of(
            new String[]{"icate", "ic"}, new String[]{"ative", ""},
            new String[]{"alize", "al"}, new String[]{"iciti", "ic"},
            new String[]{"ical", "ic"}, new String[]{"ness", ""},
            new String[]{"ful", ""}
    );

    private static final List<String> STEP4 = List.of(
            "ement", "ance", "ence", "able", "ible", "ment", "ant", "ent",
            "ism", "ate", "iti", "ous", "ive", "ize", "ion", "al", "er", "ic", "ou"
    );

    /**
     * Reduces a single word to its stem. Words of two characters or fewer are
     * returned unchanged, matching the reference implementation.
     */
    public String stem(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String w = word.toLowerCase();
        if (w.length() <= 2) {
            return w;
        }
        w = step1a(w);
        w = step1b(w);
        w = step1c(w);
        w = step2(w);
        w = step3(w);
        w = step4(w);
        w = step5(w);
        return w;
    }

    // ---------------------------------------------------------------- steps

    private String step1a(String w) {
        if (w.endsWith("sses")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("ies")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("ss")) {
            return w;
        }
        if (w.endsWith("s")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private String step1b(String w) {
        if (w.endsWith("eed")) {
            String stem = w.substring(0, w.length() - 3);
            return measure(stem) > 0 ? stem + "ee" : w;
        }
        if (w.endsWith("ed")) {
            String stem = w.substring(0, w.length() - 2);
            return containsVowel(stem) ? cleanupAfter1b(stem) : w;
        }
        if (w.endsWith("ing")) {
            String stem = w.substring(0, w.length() - 3);
            return containsVowel(stem) ? cleanupAfter1b(stem) : w;
        }
        return w;
    }

    /** Restores a readable stem after "ed"/"ing" removal (hop -> hope, fall -> fall). */
    private String cleanupAfter1b(String stem) {
        if (stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz")) {
            return stem + "e";
        }
        if (endsWithDoubleConsonant(stem)) {
            char last = stem.charAt(stem.length() - 1);
            if (last != 'l' && last != 's' && last != 'z') {
                return stem.substring(0, stem.length() - 1);
            }
            return stem;
        }
        if (measure(stem) == 1 && endsCvc(stem)) {
            return stem + "e";
        }
        return stem;
    }

    private String step1c(String w) {
        if (w.endsWith("y")) {
            String stem = w.substring(0, w.length() - 1);
            if (containsVowel(stem)) {
                return stem + "i";
            }
        }
        return w;
    }

    private String step2(String w) {
        for (String[] rule : STEP2) {
            if (w.endsWith(rule[0])) {
                String stem = w.substring(0, w.length() - rule[0].length());
                return measure(stem) > 0 ? stem + rule[1] : w;
            }
        }
        return w;
    }

    private String step3(String w) {
        for (String[] rule : STEP3) {
            if (w.endsWith(rule[0])) {
                String stem = w.substring(0, w.length() - rule[0].length());
                return measure(stem) > 0 ? stem + rule[1] : w;
            }
        }
        return w;
    }

    private String step4(String w) {
        for (String suffix : STEP4) {
            if (!w.endsWith(suffix)) {
                continue;
            }
            String stem = w.substring(0, w.length() - suffix.length());
            if (measure(stem) <= 1) {
                return w;
            }
            // "ion" is only stripped when the stem ends in s or t (action -> act).
            if (suffix.equals("ion") && !(stem.endsWith("s") || stem.endsWith("t"))) {
                return w;
            }
            return stem;
        }
        return w;
    }

    private String step5(String w) {
        String result = w;
        if (result.endsWith("e")) {
            String stem = result.substring(0, result.length() - 1);
            int m = measure(stem);
            if (m > 1 || (m == 1 && !endsCvc(stem))) {
                result = stem;
            }
        }
        if (measure(result) > 1 && endsWithDoubleConsonant(result) && result.endsWith("l")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // ----------------------------------------------------------- predicates

    private boolean isConsonant(String w, int i) {
        char c = w.charAt(i);
        if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
            return false;
        }
        // 'y' is a consonant only when preceded by a vowel (toy vs. syzygy).
        if (c == 'y') {
            return i == 0 || !isConsonant(w, i - 1);
        }
        return true;
    }

    /** Counts the number of vowel-consonant sequences, the Porter "measure". */
    private int measure(String stem) {
        int n = stem.length();
        int i = 0;
        int m = 0;
        while (i < n && isConsonant(stem, i)) {
            i++;
        }
        while (i < n) {
            while (i < n && !isConsonant(stem, i)) {
                i++;
            }
            if (i >= n) {
                break;
            }
            m++;
            while (i < n && isConsonant(stem, i)) {
                i++;
            }
        }
        return m;
    }

    private boolean containsVowel(String stem) {
        for (int i = 0; i < stem.length(); i++) {
            if (!isConsonant(stem, i)) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithDoubleConsonant(String w) {
        int n = w.length();
        return n >= 2 && w.charAt(n - 1) == w.charAt(n - 2) && isConsonant(w, n - 1);
    }

    /** True when the word ends consonant-vowel-consonant and the last is not w, x or y. */
    private boolean endsCvc(String w) {
        int n = w.length();
        if (n < 3) {
            return false;
        }
        if (!isConsonant(w, n - 1) || isConsonant(w, n - 2) || !isConsonant(w, n - 3)) {
            return false;
        }
        char c = w.charAt(n - 1);
        return c != 'w' && c != 'x' && c != 'y';
    }
}
