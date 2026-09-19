package com.aichatbot.http;

import com.aichatbot.entity.ChatMessage;
import com.aichatbot.repository.ChatMessageRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Persisted conversation history, overall and per session. */
@RestController
@RequestMapping("/api/chat/history")
public class HistoryController {

    private final ChatMessageRepository chatMessageRepository;

    public HistoryController(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @GetMapping
    public List<ChatMessage> recent() {
        return chatMessageRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @GetMapping("/{sessionId}")
    public List<ChatMessage> forSession(@PathVariable String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
