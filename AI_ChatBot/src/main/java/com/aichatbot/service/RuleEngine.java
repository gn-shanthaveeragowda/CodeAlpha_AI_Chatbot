package com.aichatbot.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rule-based half of the hybrid design.
 *
 * <p>Some questions have one correct answer that no amount of training data can
 * provide, because the answer is computed rather than remembered: the current
 * time, today's date, the result of an arithmetic expression. A statistical
 * classifier would only ever match these to the nearest stored FAQ, so they are
 * handled by deterministic regular expression rules that run before the model
 * and win outright when they fire.</p>
 */
@Service
public class RuleEngine {

    /** An answer produced by a rule, together with the rule that produced it. */
    public record RuleAnswer(String rule, String answer) {
    }

    /**
     * @param onRawText true when the rule must see the untouched message. The
     *                  normalizer strips punctuation, so an expression such as
     *                  "17 * 23" arrives as "17 23" and the arithmetic rule
     *                  would never match the normalized form.
     */
    private record Rule(String name,
                        Pattern pattern,
                        boolean onRawText,
                        java.util.function.Function<Matcher, String> handler) {
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    private final List<Rule> rules = List.of(
            new Rule("time",
                    Pattern.compile("\\b(what('?s| is)? the )?time\\b|\\bwhat time is it\\b"),
                    false,
                    matcher -> "It is " + LocalTime.now().format(TIME_FORMAT)
                            + " on this server right now."),

            new Rule("date",
                    Pattern.compile("\\b(today'?s date|what('?s| is)? (the )?date|what day is it|which day is today)\\b"),
                    false,
                    matcher -> "Today is " + LocalDate.now().format(DATE_FORMAT) + "."),

            new Rule("arithmetic",
                    Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x%])\\s*(\\d+(?:\\.\\d+)?)"),
                    true,
                    RuleEngine::calculate)
    );

    /**
     * Tries every rule against the normalized message and returns the first
     * match, or empty when no rule applies and the classifier should decide.
     */
    public Optional<RuleAnswer> apply(String normalizedMessage, String rawMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return Optional.empty();
        }
        String raw = rawMessage == null ? normalizedMessage : rawMessage.toLowerCase();
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern()
                    .matcher(rule.onRawText() ? raw : normalizedMessage);
            if (matcher.find()) {
                String answer = rule.handler().apply(matcher);
                if (answer != null) {
                    return Optional.of(new RuleAnswer(rule.name(), answer));
                }
            }
        }
        return Optional.empty();
    }

    /** Evaluates a simple two-operand expression found in the message. */
    private static String calculate(Matcher matcher) {
        try {
            double left = Double.parseDouble(matcher.group(1));
            String operator = matcher.group(2);
            double right = Double.parseDouble(matcher.group(3));

            double result = switch (operator) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*", "x" -> left * right;
                case "%" -> right == 0 ? Double.NaN : left % right;
                case "/" -> right == 0 ? Double.NaN : left / right;
                default -> Double.NaN;
            };

            if (Double.isNaN(result)) {
                return "That division has no defined answer, because dividing by zero is undefined.";
            }
            return matcher.group(1) + " " + operator + " " + matcher.group(3) + " = " + format(result);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10000.0) / 10000.0);
    }

    public int ruleCount() {
        return rules.size();
    }
}
