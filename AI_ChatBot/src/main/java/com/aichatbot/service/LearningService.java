package com.aichatbot.service;

import com.aichatbot.dto.FeedbackRequest;
import com.aichatbot.dto.TrainingRequest;
import com.aichatbot.entity.ChatMessage;
import com.aichatbot.entity.Intent;
import com.aichatbot.entity.Response;
import com.aichatbot.entity.TrainingExample;
import com.aichatbot.repository.ChatMessageRepository;
import com.aichatbot.repository.IntentRepository;
import com.aichatbot.repository.ResponseRepository;
import com.aichatbot.repository.TrainingExampleRepository;
import com.aichatbot.entity.UnansweredQuestion;
import com.aichatbot.ml.IntentClassifier;
import com.aichatbot.nlp.NLPProcessor;
import com.aichatbot.repository.UnansweredQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Turns real conversations into training data.
 *
 * <p>This is what makes the bot improve after it ships. Three things feed it:</p>
 * <ul>
 *   <li>A thumbs down with a corrected intent: the user's original wording is
 *       stored as an example of the intent it <em>should</em> have matched.</li>
 *   <li>A thumbs up on a barely-confident answer: the wording is stored as an
 *       example of the intent that did match, so the same phrasing is
 *       recognised strongly next time.</li>
 *   <li>Direct teaching through the admin API, either a new example for an
 *       existing FAQ or a whole new FAQ.</li>
 * </ul>
 *
 * <p>Every change retrains the model immediately, so the improvement is live in
 * the next message rather than after a restart.</p>
 */
@Service
public class LearningService {

    private static final Logger log = LoggerFactory.getLogger(LearningService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final TrainingExampleRepository trainingExampleRepository;
    private final IntentRepository intentRepository;
    private final ResponseRepository responseRepository;
    private final UnansweredQuestionRepository unansweredQuestionRepository;
    private final ModelTrainingService modelTrainingService;
    private final NLPProcessor nlpProcessor;

    /** Answers accepted below this confidence are worth reinforcing. */
    private final double reinforceBelow;

    /** A gap is considered closed once it classifies at least this confidently. */
    private final double confidenceThreshold;

    public LearningService(ChatMessageRepository chatMessageRepository,
                           TrainingExampleRepository trainingExampleRepository,
                           IntentRepository intentRepository,
                           ResponseRepository responseRepository,
                           UnansweredQuestionRepository unansweredQuestionRepository,
                           ModelTrainingService modelTrainingService,
                           NLPProcessor nlpProcessor,
                           @Value("${chatbot.learning.reinforce-below:0.75}") double reinforceBelow,
                           @Value("${chatbot.confidence-threshold:0.35}") double confidenceThreshold) {
        this.chatMessageRepository = chatMessageRepository;
        this.trainingExampleRepository = trainingExampleRepository;
        this.intentRepository = intentRepository;
        this.responseRepository = responseRepository;
        this.unansweredQuestionRepository = unansweredQuestionRepository;
        this.modelTrainingService = modelTrainingService;
        this.nlpProcessor = nlpProcessor;
        this.reinforceBelow = reinforceBelow;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Retrains, then closes any logged gap the model can now answer.
     *
     * <p>Teaching one topic often fixes several recorded questions at once,
     * because they were different wordings of the same thing. Re-checking them
     * keeps the training queue honest instead of showing work already done.</p>
     */
    private void retrainAndResolveGaps() {
        modelTrainingService.train();

        List<UnansweredQuestion> open = unansweredQuestionRepository.findAll().stream()
                .filter(question -> !question.isResolved())
                .toList();

        int closed = 0;
        for (UnansweredQuestion question : open) {
            List<IntentClassifier.Prediction> predictions =
                    modelTrainingService.predict(nlpProcessor.extractFeatures(question.getQuestion()));
            if (!predictions.isEmpty() && predictions.get(0).confidence() >= confidenceThreshold) {
                question.setResolved(true);
                unansweredQuestionRepository.save(question);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Retraining closed {} previously unanswered question(s)", closed);
        }
    }

    /** Outcome of a learning action, reported back to the caller. */
    public record LearningResult(boolean accepted, boolean learned, boolean retrained, String detail) {
    }

    /**
     * Records a rating and learns from it when there is something to learn.
     */
    @Transactional
    public LearningResult recordFeedback(FeedbackRequest request) {
        Optional<ChatMessage> found = chatMessageRepository.findById(request.messageId());
        if (found.isEmpty()) {
            return new LearningResult(false, false, false, "No message with that id.");
        }

        ChatMessage message = found.get();
        message.setHelpful(request.helpful());
        message.setCorrectedIntent(request.correctedIntent());
        chatMessageRepository.save(message);

        if (Boolean.FALSE.equals(request.helpful())) {
            return handleNegative(message, request.correctedIntent());
        }
        return handlePositive(message);
    }

    private LearningResult handleNegative(ChatMessage message, String correctedIntent) {
        if (correctedIntent == null || correctedIntent.isBlank()) {
            return new LearningResult(true, false, false,
                    "Thanks, noted. Tell me which topic you meant and I will learn the correction.");
        }
        Optional<Intent> intent = intentRepository.findByName(correctedIntent.trim());
        if (intent.isEmpty()) {
            return new LearningResult(true, false, false,
                    "I do not have an intent called '" + correctedIntent + "' to learn against.");
        }

        boolean learned = addExample(message.getUserMessage(), intent.get(), TrainingExample.Source.LEARNED);
        if (!learned) {
            return new LearningResult(true, false, false, "I already had that example, so nothing changed.");
        }

        markResolved(message.getUserMessage());
        retrainAndResolveGaps();
        log.info("Learned correction: '{}' now maps to intent '{}'",
                message.getUserMessage(), correctedIntent);
        return new LearningResult(true, true, true,
                "Thanks, I have learned that. Ask it again and I should get it right.");
    }

    private LearningResult handlePositive(ChatMessage message) {
        boolean worthLearning = !message.isFallback()
                && "MODEL".equals(message.getStrategy())
                && message.getConfidence() < reinforceBelow
                && message.getDetectedIntent() != null;

        if (!worthLearning) {
            return new LearningResult(true, false, false, "Thanks for the feedback.");
        }

        Optional<Intent> intent = intentRepository.findByName(message.getDetectedIntent());
        if (intent.isEmpty()) {
            return new LearningResult(true, false, false, "Thanks for the feedback.");
        }

        boolean learned = addExample(message.getUserMessage(), intent.get(), TrainingExample.Source.LEARNED);
        if (!learned) {
            return new LearningResult(true, false, false, "Thanks for the feedback.");
        }

        retrainAndResolveGaps();
        log.info("Reinforced intent '{}' with confirmed example '{}'",
                message.getDetectedIntent(), message.getUserMessage());
        return new LearningResult(true, true, true,
                "Thanks. I have added your wording to my training data for that topic.");
    }

    /** Teaches one new example for an intent that already exists. */
    @Transactional
    public LearningResult teachExample(TrainingRequest request) {
        Optional<Intent> intent = intentRepository.findByName(request.intent().trim());
        if (intent.isEmpty()) {
            return new LearningResult(false, false, false,
                    "Unknown intent '" + request.intent() + "'.");
        }
        if (!addExample(request.example(), intent.get(), TrainingExample.Source.ADMIN)) {
            return new LearningResult(true, false, false, "That example was already in the training data.");
        }
        markResolved(request.example());
        retrainAndResolveGaps();
        return new LearningResult(true, true, true,
                "Added and retrained. '" + request.example().trim() + "' now maps to " + request.intent() + ".");
    }

    /** Creates an entirely new FAQ: intent, training examples and answers. */
    @Transactional
    public LearningResult teachIntent(TrainingRequest.NewIntentRequest request) {
        String name = request.name().trim().toLowerCase().replace(' ', '_');
        if (intentRepository.existsByName(name)) {
            return new LearningResult(false, false, false, "An intent named '" + name + "' already exists.");
        }

        Intent intent = intentRepository.save(new Intent(
                name,
                request.description() == null ? name.replace('_', ' ') : request.description(),
                request.category() == null ? "Custom" : request.category()));

        responseRepository.save(new Response(
                request.primaryAnswer().trim(), Response.ResponseType.PRIMARY, intent));
        if (request.detailAnswer() != null && !request.detailAnswer().isBlank()) {
            responseRepository.save(new Response(
                    request.detailAnswer().trim(), Response.ResponseType.DETAIL, intent));
        }
        if (request.exampleAnswer() != null && !request.exampleAnswer().isBlank()) {
            responseRepository.save(new Response(
                    request.exampleAnswer().trim(), Response.ResponseType.EXAMPLE, intent));
        }

        int added = 0;
        List<String> examples = request.examples() == null ? List.of() : request.examples();
        for (String example : examples) {
            if (addExample(example, intent, TrainingExample.Source.ADMIN)) {
                added++;
                markResolved(example);
            }
        }

        if (added == 0) {
            // Without at least one example the classifier can never select the
            // intent, so the intent name itself becomes the first example.
            addExample(name.replace('_', ' '), intent, TrainingExample.Source.ADMIN);
            added = 1;
        }

        retrainAndResolveGaps();
        log.info("New intent '{}' created with {} examples", name, added);
        return new LearningResult(true, true, true,
                "Created intent '" + name + "' with " + added + " training examples and retrained.");
    }

    private boolean addExample(String text, Intent intent, TrainingExample.Source source) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (trainingExampleRepository.existsByExampleTextIgnoreCaseAndIntent_Name(trimmed, intent.getName())) {
            return false;
        }
        trainingExampleRepository.save(new TrainingExample(trimmed, intent, source));
        return true;
    }

    /** Closes the matching entry in the unanswered queue once it is taught. */
    private void markResolved(String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        unansweredQuestionRepository.findAll().stream()
                .filter(entry -> !entry.isResolved())
                .filter(entry -> entry.getQuestion().equalsIgnoreCase(question.trim()))
                .forEach(entry -> {
                    entry.setResolved(true);
                    unansweredQuestionRepository.save(entry);
                });
    }
}
