package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lexicon-based sentiment analysis with negation and intensifier handling.
 *
 * <p>The chatbot uses the result to soften its tone: a frustrated learner
 * ("I still don't understand this, it's confusing") gets an encouraging line
 * before the explanation, while a positive message gets a brief acknowledgement.</p>
 */
@Component
public class SentimentAnalyzer {

    /** Word polarity scores in the range -2 to +2. */
    private static final Map<String, Double> LEXICON = Map.ofEntries(
            Map.entry("good", 1.0), Map.entry("great", 1.5), Map.entry("excellent", 2.0),
            Map.entry("awesome", 2.0), Map.entry("amazing", 2.0), Map.entry("perfect", 2.0),
            Map.entry("love", 1.5), Map.entry("nice", 1.0), Map.entry("helpful", 1.5),
            Map.entry("clear", 1.0), Map.entry("thanks", 1.0), Map.entry("thank", 1.0),
            Map.entry("useful", 1.5), Map.entry("brilliant", 2.0), Map.entry("easy", 1.0),
            Map.entry("understood", 1.0), Map.entry("works", 1.0), Map.entry("correct", 1.0),
            Map.entry("bad", -1.0), Map.entry("terrible", -2.0), Map.entry("awful", -2.0),
            Map.entry("horrible", -2.0), Map.entry("hate", -1.5), Map.entry("useless", -2.0),
            Map.entry("confusing", -1.5), Map.entry("confused", -1.5), Map.entry("hard", -1.0),
            Map.entry("difficult", -1.0), Map.entry("wrong", -1.5), Map.entry("stuck", -1.5),
            Map.entry("frustrated", -2.0), Map.entry("frustrating", -2.0), Map.entry("annoying", -1.5),
            Map.entry("stupid", -2.0), Map.entry("boring", -1.0), Map.entry("failed", -1.5),
            Map.entry("broken", -1.5), Map.entry("worst", -2.0), Map.entry("poor", -1.0),
            Map.entry("unclear", -1.5), Map.entry("lost", -1.0), Map.entry("struggling", -1.5)
    );

    private static final Set<String> NEGATIONS = Set.of(
            "not", "no", "never", "none", "cannot", "dont", "doesnt", "didnt", "isnt", "wasnt");

    private static final Map<String, Double> INTENSIFIERS = Map.of(
            "very", 1.5, "really", 1.5, "so", 1.3, "extremely", 2.0,
            "totally", 1.5, "completely", 1.5, "quite", 1.2, "super", 1.5);

    /** Sentiment labels exposed to the API and the user interface. */
    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    public record Result(Sentiment sentiment, double score) {

        public boolean isNegative() {
            return sentiment == Sentiment.NEGATIVE;
        }

        public boolean isPositive() {
            return sentiment == Sentiment.POSITIVE;
        }
    }

    /**
     * Scores a list of normalized tokens. A negation within the two preceding
     * tokens flips the polarity, and an intensifier multiplies it.
     */
    public Result analyze(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new Result(Sentiment.NEUTRAL, 0.0);
        }

        double total = 0.0;
        int scoredWords = 0;

        for (int i = 0; i < tokens.size(); i++) {
            Double polarity = LEXICON.get(tokens.get(i));
            if (polarity == null) {
                continue;
            }
            double value = polarity;

            for (int back = 1; back <= 2 && i - back >= 0; back++) {
                String previous = tokens.get(i - back);
                if (NEGATIONS.contains(previous)) {
                    value = -value * 0.75;
                    break;
                }
                Double multiplier = INTENSIFIERS.get(previous);
                if (multiplier != null) {
                    value *= multiplier;
                }
            }

            total += value;
            scoredWords++;
        }

        if (scoredWords == 0) {
            return new Result(Sentiment.NEUTRAL, 0.0);
        }

        double normalized = clamp(total / Math.sqrt(scoredWords + 1.0), -2.0, 2.0);
        if (normalized >= 0.5) {
            return new Result(Sentiment.POSITIVE, round(normalized));
        }
        if (normalized <= -0.5) {
            return new Result(Sentiment.NEGATIVE, round(normalized));
        }
        return new Result(Sentiment.NEUTRAL, round(normalized));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
