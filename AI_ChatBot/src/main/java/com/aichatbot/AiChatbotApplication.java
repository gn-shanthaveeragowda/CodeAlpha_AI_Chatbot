package com.aichatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the CodeMate AI chatbot.
 *
 * <p>The same application serves three interfaces over one trained model:</p>
 * <pre>
 *   java -jar ai-chatbot.jar              web interface at http://localhost:8081
 *   java -jar ai-chatbot.jar --gui        web interface plus a Swing desktop window
 *   java -jar ai-chatbot.jar --console    web interface plus an interactive terminal
 * </pre>
 */
@SpringBootApplication
public class AiChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatbotApplication.class, args);
    }
}
