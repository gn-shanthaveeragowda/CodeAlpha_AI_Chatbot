package com.aichatbot.gui;

import com.aichatbot.dto.ChatRequest;
import com.aichatbot.dto.ChatResponse;
import com.aichatbot.service.ChatbotService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.UUID;

/**
 * Terminal interface, started with {@code --console}.
 *
 * <p>Useful for demonstrating the bot without a browser and for quickly testing
 * the classifier: typing {@code :debug} shows the intent, confidence, engine and
 * extracted features for every following answer.</p>
 */
@Component
@Order(3)
public class ConsoleChatRunner implements ApplicationRunner {

    private final ChatbotService chatbotService;
    private final String sessionId = "console-" + UUID.randomUUID();

    public ConsoleChatRunner(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("console") && !args.getNonOptionArgs().contains("--console")) {
            return;
        }
        Thread session = new Thread(this::loop, "console-chat");
        session.setDaemon(true);
        session.start();
    }

    private void loop() {
        boolean debug = false;
        System.out.println();
        System.out.println("===============================================");
        System.out.println("  CodeMate - AI Chatbot (console mode)");
        System.out.println("  Type a question, :debug to toggle details,");
        System.out.println("  or 'exit' to leave.");
        System.out.println("===============================================");
        System.out.println();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (true) {
                System.out.print("You > ");
                System.out.flush();
                line = reader.readLine();
                if (line == null) {
                    return;
                }
                String message = line.trim();
                if (message.isEmpty()) {
                    continue;
                }
                if (message.equalsIgnoreCase(":debug")) {
                    debug = !debug;
                    System.out.println("      debug " + (debug ? "on" : "off"));
                    continue;
                }
                if (message.toLowerCase(Locale.ROOT).matches("exit|quit|:q")) {
                    System.out.println("Bot > Goodbye!");
                    return;
                }

                ChatResponse response = chatbotService.respond(new ChatRequest(message, sessionId));
                System.out.println("Bot > " + response.response());
                if (debug) {
                    System.out.printf("      intent=%s confidence=%.3f engine=%s sentiment=%s time=%dms%n",
                            response.intent(), response.confidence(), response.strategy(),
                            response.sentiment(), response.responseTimeMs());
                    System.out.println("      features=" + response.tokens());
                    if (!response.alternatives().isEmpty()) {
                        System.out.println("      alternatives=" + response.alternatives());
                    }
                }
                System.out.println();
            }
        } catch (Exception e) {
            System.out.println("Console session ended: " + e.getMessage());
        }
    }
}
