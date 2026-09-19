package com.aichatbot.repository;

import com.aichatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop100ByOrderByCreatedAtDesc();

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    long countByFallbackTrue();

    long countByHelpfulTrue();

    long countByHelpfulFalse();

    List<ChatMessage> findByCreatedAtAfter(LocalDateTime after);
}
