package com.aichatbot.http;

import com.aichatbot.dto.ChatRequest;
import com.aichatbot.dto.ChatResponse;
import com.aichatbot.service.ChatbotService;
import com.aichatbot.service.ConversationContextService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The conversation endpoint used by the web interface and the desktop client. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatbotService chatbotService;
    private final ConversationContextService contextService;

    public ChatController(ChatbotService chatbotService, ConversationContextService contextService) {
        this.chatbotService = chatbotService;
        this.contextService = contextService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatbotService.respond(request);
    }

    /** Forgets the conversation so follow-ups start fresh. */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, String> resetSession(@PathVariable String sessionId) {
        contextService.reset(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }
}
