package com.aichatbot.gui;

import com.aichatbot.dto.ChatRequest;
import com.aichatbot.dto.ChatResponse;
import com.aichatbot.service.ChatbotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Desktop interface for the same chatbot, started with {@code --gui}.
 *
 * <p>It calls {@link ChatbotService} directly rather than going over HTTP,
 * which demonstrates that the conversation engine is independent of the
 * delivery channel: the web page, this window and the console all share one
 * trained model and one piece of dialogue logic.</p>
 *
 * <p>The window only opens when the flag is present and a display is available,
 * so the application still runs headless on a server.</p>
 */
@Component
@Order(2)
public class SwingChatbotClient implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SwingChatbotClient.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color INK = new Color(0x18, 0x24, 0x1E);
    private static final Color GREEN = new Color(0x17, 0x76, 0x5E);
    private static final Color PAPER = new Color(0xF4, 0xF7, 0xF1);

    private final ChatbotService chatbotService;
    private final String sessionId = "swing-" + UUID.randomUUID();

    private JTextArea transcript;
    private JTextField input;
    private JLabel statusBar;

    public SwingChatbotClient(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("gui") && !args.getNonOptionArgs().contains("--gui")) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("--gui was requested but no display is available; use the web interface instead.");
            return;
        }
        SwingUtilities.invokeLater(this::buildWindow);
    }

    private void buildWindow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a perfectly good fallback.
        }

        JFrame frame = new JFrame("CodeMate - AI Chatbot (Desktop)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(720, 560));

        transcript = new JTextArea();
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        transcript.setBackground(PAPER);
        transcript.setForeground(INK);
        transcript.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        input = new JTextField();
        input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDC, 0xE6, 0xDE)),
                BorderFactory.createEmptyBorder(9, 11, 9, 11)));
        input.addActionListener(event -> send());

        JButton sendButton = new JButton("Send");
        sendButton.setBackground(GREEN);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.addActionListener(event -> send());

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        composer.add(input, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);

        statusBar = new JLabel(" Ready.");
        statusBar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBar.setForeground(new Color(0x71, 0x80, 0x78));
        statusBar.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(composer);
        south.add(statusBar);
        south.add(Box.createVerticalStrut(2));

        frame.setLayout(new BorderLayout());
        frame.add(header(), BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);

        append("CodeMate", "Hello! Ask me about Java, OOP, collections, SQL or Spring Boot. "
                + "Say \"tell me more\" to go deeper on the last topic.");
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        input.requestFocusInWindow();
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x0D, 0x50, 0x3F));
        header.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("CodeMate");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

        JLabel subtitle = new JLabel("Java learning assistant - NLP and machine learning intent engine");
        subtitle.setForeground(new Color(0xA9, 0xD6, 0xBC));
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);

        header.add(text, BorderLayout.WEST);
        return header;
    }

    private void send() {
        String message = input.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        input.setText("");
        append("You", message);

        try {
            ChatResponse response = chatbotService.respond(new ChatRequest(message, sessionId));
            append("CodeMate", response.response());
            statusBar.setText(String.format(
                    " intent: %s   confidence: %.0f%%   engine: %s   sentiment: %s   %d ms",
                    response.intent(), response.confidence() * 100, response.strategy(),
                    response.sentiment(), response.responseTimeMs()));
            if (!response.suggestions().isEmpty()) {
                append("", "Try next: " + String.join("  |  ", response.suggestions()));
            }
        } catch (RuntimeException e) {
            append("CodeMate", "Something went wrong handling that: " + e.getMessage());
            statusBar.setText(" Error.");
        }
        input.requestFocusInWindow();
    }

    private void append(String speaker, String text) {
        if (speaker.isEmpty()) {
            transcript.append("          " + text + "\n\n");
        } else {
            transcript.append("[" + java.time.LocalTime.now().format(CLOCK) + "] "
                    + speaker + ":\n" + text + "\n\n");
        }
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }
}
