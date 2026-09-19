package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * First stage of the NLP pipeline: turns raw user input into clean lowercase
 * text that the tokenizer can work with.
 *
 * <p>Handles accents, contractions ("what's" to "what is"), chat shorthand
 * ("u" to "you"), repeated characters ("heeeelp" to "help") and punctuation.</p>
 */
@Component
public class TextNormalizer {

    private static final Pattern NON_TEXT = Pattern.compile("[^a-z0-9+#\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ELONGATION = Pattern.compile("([a-z])\\1{2,}");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    /** Contractions are expanded before punctuation is stripped. */
    private static final Map<String, String> CONTRACTIONS = Map.ofEntries(
            Map.entry("what's", "what is"), Map.entry("whats", "what is"),
            Map.entry("who's", "who is"), Map.entry("how's", "how is"),
            Map.entry("that's", "that is"), Map.entry("it's", "it is"),
            Map.entry("i'm", "i am"), Map.entry("i've", "i have"),
            Map.entry("i'd", "i would"), Map.entry("i'll", "i will"),
            Map.entry("you're", "you are"), Map.entry("youre", "you are"),
            Map.entry("don't", "do not"), Map.entry("dont", "do not"),
            Map.entry("doesn't", "does not"), Map.entry("didn't", "did not"),
            Map.entry("can't", "can not"), Map.entry("cant", "can not"),
            Map.entry("won't", "will not"), Map.entry("isn't", "is not"),
            Map.entry("aren't", "are not"), Map.entry("wasn't", "was not"),
            Map.entry("couldn't", "could not"), Map.entry("shouldn't", "should not"),
            Map.entry("wouldn't", "would not"), Map.entry("let's", "let us"),
            Map.entry("there's", "there is"), Map.entry("here's", "here is")
    );

    /** Informal chat shorthand seen constantly in real chatbot traffic. */
    private static final Map<String, String> SHORTHAND = Map.ofEntries(
            Map.entry("u", "you"), Map.entry("ur", "your"), Map.entry("r", "are"),
            Map.entry("pls", "please"), Map.entry("plz", "please"),
            Map.entry("thx", "thanks"), Map.entry("ty", "thanks"),
            Map.entry("tq", "thanks"), Map.entry("hlp", "help"),
            Map.entry("abt", "about"), Map.entry("bcz", "because"),
            Map.entry("bcoz", "because"), Map.entry("coz", "because"),
            Map.entry("wat", "what"), Map.entry("wht", "what"),
            Map.entry("hw", "how"), Map.entry("diff", "difference"),
            Map.entry("info", "information"), Map.entry("eg", "example"),
            Map.entry("prog", "program"), Map.entry("lang", "language")
    );

    /**
     * Normalizes raw input to lowercase alphanumeric text.
     * The characters '+' and '#' survive so language names such as c++ and c#
     * are not destroyed.
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = Normalizer.normalize(raw, Normalizer.Form.NFD);
        text = ACCENTS.matcher(text).replaceAll("");
        text = text.toLowerCase();

        for (Map.Entry<String, String> entry : CONTRACTIONS.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        text = NON_TEXT.matcher(text).replaceAll(" ");
        text = ELONGATION.matcher(text).replaceAll("$1$1");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();

        return expandShorthand(text);
    }

    private String expandShorthand(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String[] words = text.split(" ");
        StringBuilder builder = new StringBuilder(text.length());
        for (String word : words) {
            builder.append(SHORTHAND.getOrDefault(word, word)).append(' ');
        }
        return builder.toString().trim();
    }
}
