package com.aichatbot.service;

import com.aichatbot.entity.ChatMessage;
import com.aichatbot.entity.TrainingExample;
import com.aichatbot.entity.UnansweredQuestion;
import com.aichatbot.repository.ChatMessageRepository;
import com.aichatbot.repository.IntentRepository;
import com.aichatbot.repository.ResponseRepository;
import com.aichatbot.repository.TrainingExampleRepository;
import com.aichatbot.repository.UnansweredQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates conversation history into the numbers shown on the Insights tab.
 *
 * <p>The useful signal for a chatbot is not how many messages it handled but
 * how often it had to fall back, how confident it was when it did answer and
 * which questions it keeps failing. Those three together tell you exactly where
 * the next training examples should go.</p>
 */
@Service
public class AnalyticsService {

    private final ChatMessageRepository chatMessageRepository;
    private final IntentRepository intentRepository;
    private final TrainingExampleRepository trainingExampleRepository;
    private final ResponseRepository responseRepository;
    private final UnansweredQuestionRepository unansweredQuestionRepository;
    private final ConversationContextService contextService;

    public AnalyticsService(ChatMessageRepository chatMessageRepository,
                            IntentRepository intentRepository,
                            TrainingExampleRepository trainingExampleRepository,
                            ResponseRepository responseRepository,
                            UnansweredQuestionRepository unansweredQuestionRepository,
                            ConversationContextService contextService) {
        this.chatMessageRepository = chatMessageRepository;
        this.intentRepository = intentRepository;
        this.trainingExampleRepository = trainingExampleRepository;
        this.responseRepository = responseRepository;
        this.unansweredQuestionRepository = unansweredQuestionRepository;
        this.contextService = contextService;
    }

    /** Everything the dashboard needs in one call. */
    public record Analytics(long totalMessages,
                            long answeredMessages,
                            long fallbackMessages,
                            double fallbackRate,
                            double answerRate,
                            double averageConfidence,
                            double averageResponseTimeMs,
                            long positiveFeedback,
                            long negativeFeedback,
                            double satisfactionRate,
                            int activeSessions,
                            long knownIntents,
                            long trainingExamples,
                            long learnedExamples,
                            long storedAnswers,
                            long openTrainingGaps,
                            Map<String, Long> topIntents,
                            Map<String, Long> strategyBreakdown,
                            Map<String, Long> sentimentBreakdown,
                            List<GapEntry> trainingQueue) {
    }

    /** One unanswered question awaiting training. */
    public record GapEntry(Long id, String question, int timesAsked,
                           double bestConfidence, String closestIntent) {
    }

    @Transactional(readOnly = true)
    public Analytics snapshot() {
        List<ChatMessage> messages = chatMessageRepository.findAll();

        long total = messages.size();
        long fallbacks = messages.stream().filter(ChatMessage::isFallback).count();
        long answered = total - fallbacks;

        double averageConfidence = messages.stream()
                .filter(message -> !message.isFallback())
                .mapToDouble(ChatMessage::getConfidence)
                .average()
                .orElse(0.0);

        double averageResponseTime = messages.stream()
                .mapToLong(ChatMessage::getResponseTimeMs)
                .average()
                .orElse(0.0);

        long positive = messages.stream().filter(m -> Boolean.TRUE.equals(m.getHelpful())).count();
        long negative = messages.stream().filter(m -> Boolean.FALSE.equals(m.getHelpful())).count();
        long rated = positive + negative;

        Map<String, Long> topIntents = countBy(messages.stream()
                .filter(message -> !message.isFallback())
                .map(ChatMessage::getDetectedIntent)
                .toList(), 8);

        Map<String, Long> strategies = countBy(messages.stream()
                .map(ChatMessage::getStrategy)
                .toList(), 10);

        Map<String, Long> sentiments = countBy(messages.stream()
                .map(ChatMessage::getSentiment)
                .toList(), 5);

        List<GapEntry> queue = unansweredQuestionRepository
                .findTop20ByResolvedFalseOrderByTimesAskedDescLastAskedAtDesc()
                .stream()
                .map(this::toGapEntry)
                .toList();

        return new Analytics(
                total,
                answered,
                fallbacks,
                rate(fallbacks, total),
                rate(answered, total),
                round(averageConfidence),
                round(averageResponseTime),
                positive,
                negative,
                rate(positive, rated),
                contextService.activeSessions(),
                intentRepository.count(),
                trainingExampleRepository.count(),
                trainingExampleRepository.countBySource(TrainingExample.Source.LEARNED),
                responseRepository.count(),
                unansweredQuestionRepository.countByResolvedFalse(),
                topIntents,
                strategies,
                sentiments,
                queue);
    }

    private GapEntry toGapEntry(UnansweredQuestion question) {
        return new GapEntry(question.getId(), question.getQuestion(), question.getTimesAsked(),
                round(question.getBestConfidence()), question.getClosestIntent());
    }

    /** Counts occurrences and keeps the most frequent entries, highest first. */
    private Map<String, Long> countBy(List<String> values, int limit) {
        Map<String, Long> counts = new HashMap<>();
        for (String value : values) {
            if (value != null) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        Map<String, Long> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    private double rate(long part, long total) {
        return total == 0 ? 0.0 : round((double) part / total);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
