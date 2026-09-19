package com.aichatbot.service;

import com.aichatbot.dto.ChatRequest;
import com.aichatbot.dto.ChatResponse;
import com.aichatbot.entity.ChatMessage;
import com.aichatbot.entity.Intent;
import com.aichatbot.entity.Response;
import com.aichatbot.entity.UnansweredQuestion;
import com.aichatbot.ml.CosineSimilarityClassifier;
import com.aichatbot.ml.IntentClassifier;
import com.aichatbot.nlp.NLPProcessor;
import com.aichatbot.nlp.SentimentAnalyzer;
import com.aichatbot.repository.ChatMessageRepository;
import com.aichatbot.repository.IntentRepository;
import com.aichatbot.repository.ResponseRepository;
import com.aichatbot.repository.UnansweredQuestionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides what the chatbot says.
 *
 * <p>One message travels through four answering strategies in priority order:</p>
 * <ol>
 *   <li><b>RULE</b> - a deterministic rule computed the answer (time, date, maths).</li>
 *   <li><b>CONTEXT</b> - the message is a follow-up such as "tell me more", so the
 *       previous topic is continued with a deeper answer.</li>
 *   <li><b>MODEL</b> - the ensemble classifier matched an intent confidently.</li>
 *   <li><b>FALLBACK</b> - nothing matched well enough, so the bot says so honestly,
 *       offers the closest topics and records the question for training.</li>
 * </ol>
 *
 * <p>Answering with a wrong but confident reply is worse than admitting
 * uncertainty, which is why the middle confidence band asks a clarifying
 * question instead of guessing.</p>
 */
@Service
public class ChatbotService {

    private static final String FALLBACK_INTENT = "fallback";

    private static final List<String> FALLBACK_RESPONSES = List.of(
            "I do not have a confident answer for that yet, and I have noted the question so it can be added to my training data.",
            "That one is outside what I have been trained on so far, so I have logged it to learn later.",
            "I am not sure about that yet, and I would rather say so than guess.");

    private static final List<String> ENCOURAGEMENT = List.of(
            "That topic trips up a lot of people, so you are in good company. ",
            "This one usually takes a couple of readings to click, which is completely normal. ",
            "Let us slow it down and take it a piece at a time. ");

    private final NLPProcessor nlpProcessor;
    private final RuleEngine ruleEngine;
    private final ConversationContextService contextService;
    private final ModelTrainingService modelService;
    private final ResponseRepository responseRepository;
    private final IntentRepository intentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UnansweredQuestionRepository unansweredQuestionRepository;

    private final double confidenceThreshold;
    private final double clarifyThreshold;

    public ChatbotService(NLPProcessor nlpProcessor,
                          RuleEngine ruleEngine,
                          ConversationContextService contextService,
                          ModelTrainingService modelService,
                          ResponseRepository responseRepository,
                          IntentRepository intentRepository,
                          ChatMessageRepository chatMessageRepository,
                          UnansweredQuestionRepository unansweredQuestionRepository,
                          @Value("${chatbot.confidence-threshold:0.35}") double confidenceThreshold,
                          @Value("${chatbot.clarify-threshold:0.18}") double clarifyThreshold) {
        this.nlpProcessor = nlpProcessor;
        this.ruleEngine = ruleEngine;
        this.contextService = contextService;
        this.modelService = modelService;
        this.responseRepository = responseRepository;
        this.intentRepository = intentRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.unansweredQuestionRepository = unansweredQuestionRepository;
        this.confidenceThreshold = confidenceThreshold;
        this.clarifyThreshold = clarifyThreshold;
    }

    @Transactional
    public ChatResponse respond(ChatRequest request) {
        long start = System.nanoTime();
        String sessionId = request.sessionOrDefault();
        String message = request.message().trim();

        NLPProcessor.Analysis analysis = nlpProcessor.analyze(message);
        Turn turn = decide(sessionId, message, analysis);

        long elapsedMs = Math.max(1, (System.nanoTime() - start) / 1_000_000);

        ChatMessage saved = chatMessageRepository.save(new ChatMessage(
                sessionId,
                message,
                turn.intent(),
                turn.answer(),
                turn.confidence(),
                turn.fallback(),
                analysis.sentiment().sentiment().name(),
                analysis.sentiment().score(),
                turn.strategy(),
                elapsedMs));

        contextService.remember(sessionId, message,
                turn.fallback() ? null : turn.intent(), turn.responseType());

        return new ChatResponse(
                saved.getId(),
                sessionId,
                message,
                turn.intent(),
                turn.intentDescription(),
                turn.answer(),
                round(turn.confidence()),
                turn.fallback(),
                turn.strategy(),
                analysis.sentiment().sentiment().name(),
                analysis.sentiment().score(),
                analysis.questionType().name(),
                analysis.features(),
                turn.matchedExample(),
                turn.alternatives(),
                turn.suggestions(),
                elapsedMs,
                LocalDateTime.now());
    }

    /** The internal outcome of one turn, before it is persisted. */
    private record Turn(String intent,
                        String intentDescription,
                        String answer,
                        double confidence,
                        boolean fallback,
                        String strategy,
                        Response.ResponseType responseType,
                        String matchedExample,
                        List<ChatResponse.IntentScore> alternatives,
                        List<String> suggestions) {
    }

    private Turn decide(String sessionId, String message, NLPProcessor.Analysis analysis) {
        if (analysis.isEmpty()) {
            return new Turn(FALLBACK_INTENT, "No recognisable words",
                    "I could not find any words to work with there. Try a full question, such as \"What is polymorphism?\"",
                    0.0, true, "FALLBACK", Response.ResponseType.PRIMARY, null, List.of(), popularTopics());
        }

        // 1. Deterministic rules win outright.
        Optional<RuleEngine.RuleAnswer> rule = ruleEngine.apply(analysis.normalized(), analysis.original());
        if (rule.isPresent()) {
            return new Turn("rule:" + rule.get().rule(), "Answered by a deterministic rule",
                    rule.get().answer(), 1.0, false, "RULE",
                    Response.ResponseType.PRIMARY, null, List.of(), List.of());
        }

        // 2. Follow-up on the previous topic.
        Turn followUpTurn = tryFollowUp(sessionId, analysis);
        if (followUpTurn != null) {
            return followUpTurn;
        }

        // 3. Classify with the trained ensemble.
        List<IntentClassifier.Prediction> predictions = modelService.predict(analysis.features());
        if (predictions.isEmpty()) {
            return fallbackTurn(message, analysis, 0.0, null, List.of());
        }

        IntentClassifier.Prediction top = predictions.get(0);
        List<ChatResponse.IntentScore> alternatives = alternatives(predictions);

        if (top.confidence() >= confidenceThreshold) {
            Response.ResponseType wanted =
                    analysis.questionType() == NLPProcessor.QuestionType.EXAMPLE
                            ? Response.ResponseType.EXAMPLE
                            : Response.ResponseType.PRIMARY;

            Optional<Response> answer = selectResponse(top.intent(), wanted, true);
            if (answer.isEmpty()) {
                return fallbackTurn(message, analysis, top.confidence(), top.intent(), alternatives);
            }

            String text = answer.get().getResponseText();
            if (analysis.sentiment().isNegative()) {
                text = pick(ENCOURAGEMENT) + text;
            }

            return new Turn(top.intent(), describe(top.intent()), text, top.confidence(), false,
                    "MODEL", answer.get().getResponseType(), matchedExample(analysis),
                    alternatives, followUpSuggestions(top.intent(), answer.get().getResponseType()));
        }

        // 4. Middle confidence band: ask rather than guess.
        if (top.confidence() >= clarifyThreshold) {
            List<String> options = new ArrayList<>();
            options.add(readable(top.intent()));
            for (ChatResponse.IntentScore score : alternatives) {
                options.add(readable(score.intent()));
            }
            String question = "I am not fully sure what you are asking. Did you mean "
                    + joinOptions(options) + "?";

            recordUnanswered(message, analysis, top.confidence(), top.intent());
            return new Turn(FALLBACK_INTENT, "Low confidence, clarification requested",
                    question, top.confidence(), true, "FALLBACK",
                    Response.ResponseType.PRIMARY, matchedExample(analysis), alternatives, options);
        }

        return fallbackTurn(message, analysis, top.confidence(), top.intent(), alternatives);
    }

    /** Continues the previous topic when the message is "tell me more" or similar. */
    private Turn tryFollowUp(String sessionId, NLPProcessor.Analysis analysis) {
        ConversationContextService.FollowUp followUp =
                contextService.detectFollowUp(sessionId, analysis.normalized());
        if (followUp == ConversationContextService.FollowUp.NONE) {
            return null;
        }
        Optional<String> lastIntent = contextService.lastIntent(sessionId);
        if (lastIntent.isEmpty()) {
            return null;
        }

        String intent = lastIntent.get();
        Response.ResponseType wanted =
                followUp == ConversationContextService.FollowUp.EXAMPLE
                        ? Response.ResponseType.EXAMPLE
                        : Response.ResponseType.DETAIL;

        Optional<Response> deeper = selectResponse(intent, wanted, false);
        if (deeper.isPresent()) {
            return new Turn(intent, describe(intent), deeper.get().getResponseText(), 1.0, false,
                    "CONTEXT", deeper.get().getResponseType(), null, List.of(),
                    followUpSuggestions(intent, deeper.get().getResponseType()));
        }

        return new Turn(intent, describe(intent),
                "That is everything I have stored on " + readable(intent)
                        + " for now. Ask me about a related topic and I will take it from there.",
                0.9, false, "CONTEXT", Response.ResponseType.PRIMARY, null, List.of(), popularTopics());
    }

    private Turn fallbackTurn(String message, NLPProcessor.Analysis analysis,
                              double confidence, String closestIntent,
                              List<ChatResponse.IntentScore> alternatives) {
        recordUnanswered(message, analysis, confidence, closestIntent);
        return new Turn(FALLBACK_INTENT, "No confident match",
                pick(FALLBACK_RESPONSES) + " In the meantime, try one of these topics.",
                confidence, true, "FALLBACK", Response.ResponseType.PRIMARY,
                null, alternatives, popularTopics());
    }

    /**
     * Picks one stored answer of the requested kind, choosing at random when
     * several variants exist so the bot does not sound like a recording. With
     * fallback enabled it degrades EXAMPLE to DETAIL to PRIMARY rather than
     * failing when a layer was never written.
     */
    private Optional<Response> selectResponse(String intent, Response.ResponseType type, boolean allowFallback) {
        List<Response> matches = responseRepository.findByIntentNameAndResponseType(intent, type);
        if (!matches.isEmpty()) {
            return Optional.of(matches.get(ThreadLocalRandom.current().nextInt(matches.size())));
        }
        if (!allowFallback) {
            return Optional.empty();
        }
        if (type == Response.ResponseType.EXAMPLE) {
            Optional<Response> detail = selectResponse(intent, Response.ResponseType.DETAIL, false);
            if (detail.isPresent()) {
                return detail;
            }
            return selectResponse(intent, Response.ResponseType.PRIMARY, false);
        }
        if (type == Response.ResponseType.DETAIL) {
            return selectResponse(intent, Response.ResponseType.PRIMARY, false);
        }
        return Optional.empty();
    }

    /** Offers the natural next step: more detail, then a code example. */
    private List<String> followUpSuggestions(String intent, Response.ResponseType servedType) {
        List<String> suggestions = new ArrayList<>();
        if (servedType != Response.ResponseType.DETAIL
                && !responseRepository.findByIntentNameAndResponseType(
                        intent, Response.ResponseType.DETAIL).isEmpty()) {
            suggestions.add("Tell me more");
        }
        if (servedType != Response.ResponseType.EXAMPLE
                && !responseRepository.findByIntentNameAndResponseType(
                        intent, Response.ResponseType.EXAMPLE).isEmpty()) {
            suggestions.add("Show me an example");
        }
        return suggestions;
    }

    private List<ChatResponse.IntentScore> alternatives(List<IntentClassifier.Prediction> predictions) {
        List<ChatResponse.IntentScore> scores = new ArrayList<>();
        for (int i = 1; i < predictions.size() && scores.size() < 3; i++) {
            IntentClassifier.Prediction prediction = predictions.get(i);
            if (prediction.confidence() < 0.05) {
                break;
            }
            scores.add(new ChatResponse.IntentScore(
                    prediction.intent(), round(prediction.confidence())));
        }
        return scores;
    }

    /** The stored question closest to what the user typed, shown as evidence. */
    private String matchedExample(NLPProcessor.Analysis analysis) {
        List<CosineSimilarityClassifier.Neighbour> neighbours =
                modelService.nearestExamples(analysis.features());
        return neighbours.isEmpty() ? null : neighbours.get(0).text();
    }

    /**
     * Stores a question the bot could not answer, merging repeats so the
     * training queue shows what people actually keep asking.
     */
    private void recordUnanswered(String message, NLPProcessor.Analysis analysis,
                                  double confidence, String closestIntent) {
        String key = analysis.normalized();
        if (key.isBlank()) {
            return;
        }
        Optional<UnansweredQuestion> existing =
                unansweredQuestionRepository.findByNormalizedQuestion(key);
        if (existing.isPresent()) {
            existing.get().recordRepeat(confidence, closestIntent);
            unansweredQuestionRepository.save(existing.get());
        } else {
            unansweredQuestionRepository.save(
                    new UnansweredQuestion(message, key, confidence, closestIntent));
        }
    }

    private List<String> popularTopics() {
        return List.of("What is OOP?", "Explain inheritance", "What is a HashMap?",
                "What is JDBC?", "What can you help me with?");
    }

    private String describe(String intentName) {
        return intentRepository.findByName(intentName)
                .map(Intent::getDescription)
                .orElse(readable(intentName));
    }

    private String readable(String intentName) {
        return intentName == null ? "that topic" : intentName.replace('_', ' ');
    }

    private String joinOptions(List<String> options) {
        if (options.size() == 1) {
            return options.get(0);
        }
        return String.join(", ", options.subList(0, options.size() - 1))
                + " or " + options.get(options.size() - 1);
    }

    private String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** Sentiment of a message without running a full turn, used by the API. */
    public SentimentAnalyzer.Result sentimentOf(String message) {
        return nlpProcessor.analyze(message).sentiment();
    }
}
