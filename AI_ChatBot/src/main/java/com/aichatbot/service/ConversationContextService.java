package com.aichatbot.service;

import com.aichatbot.entity.Response;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-term memory for each conversation.
 *
 * <p>Without it, "tell me more" is a meaningless question: the bot has no idea
 * what "more" refers to and would fall back. By remembering the last intent per
 * session, a follow-up can be resolved to that topic and answered with the
 * DETAIL or EXAMPLE layer instead of repeating the primary answer.</p>
 *
 * <p>State lives in memory rather than the database because it is genuinely
 * transient. Idle sessions are dropped after a timeout so the map cannot grow
 * without bound.</p>
 */
@Service
public class ConversationContextService {

    private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);
    private static final int MAX_SESSIONS = 1000;
    private static final int TOPIC_HISTORY = 5;

    /** Phrases that ask for a deeper explanation of the previous answer. */
    private static final Set<String> MORE_TRIGGERS = Set.of(
            "more", "tell me more", "more please", "explain more", "explain further",
            "go on", "continue", "elaborate", "in detail", "more detail", "more details",
            "explain in detail", "i want to know more", "and", "then", "deeper", "go deeper",
            "can you explain more", "tell more", "say more", "expand");

    /** Phrases that ask for a code sample of the previous answer. */
    private static final Set<String> EXAMPLE_TRIGGERS = Set.of(
            "example", "an example", "give an example", "give me an example",
            "show me an example", "show an example", "code", "show me code",
            "give me code", "sample code", "code example", "show code",
            "with example", "any example", "demo", "show me a demo", "example please",
            "can you show an example", "code please");

    /** Per-session state. */
    public static class Context {
        private String lastIntent;
        private String lastQuestion;
        private Response.ResponseType lastResponseType = Response.ResponseType.PRIMARY;
        private final Deque<String> recentIntents = new ArrayDeque<>();
        private int turns;
        private LocalDateTime lastSeen = LocalDateTime.now();

        public String lastIntent() {
            return lastIntent;
        }

        public String lastQuestion() {
            return lastQuestion;
        }

        public Response.ResponseType lastResponseType() {
            return lastResponseType;
        }

        public List<String> recentIntents() {
            return List.copyOf(recentIntents);
        }

        public int turns() {
            return turns;
        }

        public LocalDateTime lastSeen() {
            return lastSeen;
        }
    }

    /** What a follow-up phrase is asking for. */
    public enum FollowUp { DETAIL, EXAMPLE, NONE }

    private final Map<String, Context> sessions = new ConcurrentHashMap<>();

    public Context context(String sessionId) {
        evictStaleSessions();
        return sessions.computeIfAbsent(sessionId, key -> new Context());
    }

    /**
     * Works out whether a short message is a follow-up to the previous answer.
     * Returns NONE when the message stands on its own or there is nothing to
     * follow up on.
     */
    public FollowUp detectFollowUp(String sessionId, String normalizedMessage) {
        Context context = context(sessionId);
        if (context.lastIntent == null || normalizedMessage == null || normalizedMessage.isBlank()) {
            return FollowUp.NONE;
        }

        String trimmed = normalizedMessage.trim();
        if (EXAMPLE_TRIGGERS.contains(trimmed)) {
            return FollowUp.EXAMPLE;
        }
        if (MORE_TRIGGERS.contains(trimmed)) {
            return FollowUp.DETAIL;
        }

        // Longer phrasings such as "ok now show me an example of that" still
        // count, but only when the message carries no topic of its own.
        boolean referential = trimmed.endsWith(" that") || trimmed.endsWith(" it")
                || trimmed.endsWith(" this") || trimmed.contains("about that")
                || trimmed.contains("about it");
        if (referential && trimmed.split(" ").length <= 8) {
            if (trimmed.contains("example") || trimmed.contains("code")) {
                return FollowUp.EXAMPLE;
            }
            if (trimmed.contains("more") || trimmed.contains("detail") || trimmed.contains("explain")) {
                return FollowUp.DETAIL;
            }
        }
        return FollowUp.NONE;
    }

    /** Records the outcome of a turn so the next one can build on it. */
    public void remember(String sessionId, String question, String intent,
                         Response.ResponseType responseType) {
        Context context = context(sessionId);
        context.turns++;
        context.lastSeen = LocalDateTime.now();
        context.lastQuestion = question;
        context.lastResponseType = responseType;

        if (intent != null) {
            context.lastIntent = intent;
            if (context.recentIntents.isEmpty() || !intent.equals(context.recentIntents.peekLast())) {
                context.recentIntents.addLast(intent);
                while (context.recentIntents.size() > TOPIC_HISTORY) {
                    context.recentIntents.removeFirst();
                }
            }
        }
    }

    public Optional<String> lastIntent(String sessionId) {
        return Optional.ofNullable(context(sessionId).lastIntent);
    }

    /** Clears one conversation, used by the "new session" button. */
    public void reset(String sessionId) {
        sessions.remove(sessionId);
    }

    public int activeSessions() {
        return sessions.size();
    }

    private void evictStaleSessions() {
        if (sessions.size() < MAX_SESSIONS) {
            LocalDateTime cutoff = LocalDateTime.now().minus(SESSION_TIMEOUT);
            sessions.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));
            return;
        }
        // Hard cap reached: drop the oldest sessions regardless of the timeout.
        sessions.entrySet().stream()
                .sorted((a, b) -> a.getValue().lastSeen.compareTo(b.getValue().lastSeen))
                .limit(Math.max(1, sessions.size() - MAX_SESSIONS + 1))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(sessions::remove);
    }
}
