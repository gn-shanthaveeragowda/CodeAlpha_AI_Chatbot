package com.aichatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One stored answer for an intent.
 *
 * <p>Each intent holds several answers of different kinds, which is what lets
 * the bot follow up. "What is inheritance" returns the PRIMARY answer, "tell me
 * more" returns DETAIL, and "show an example" returns EXAMPLE, so a
 * conversation can go deeper instead of repeating one canned line.</p>
 */
@Entity
@Table(name = "responses")
public class Response {

    /** The role an answer plays in a conversation. */
    public enum ResponseType { PRIMARY, DETAIL, EXAMPLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2000)
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResponseType responseType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false)
    private Intent intent;

    protected Response() {
    }

    public Response(String responseText, ResponseType responseType, Intent intent) {
        this.responseText = responseText;
        this.responseType = responseType;
        this.intent = intent;
    }

    public Long getId() {
        return id;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public Intent getIntent() {
        return intent;
    }
}
