package com.example.baedalhankki.refund.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookInbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String payloadHash;
    @Column(nullable = false, length = 64)
    private String providerOrderId;
    private Instant receivedAt;
    private Instant processedAt;
    private Instant nextAttemptAt;
    private String lastError;
    private int attempts;

    public static WebhookInbox create(String hash, String providerOrderId, Instant now) {
        WebhookInbox inbox = new WebhookInbox();
        inbox.payloadHash = hash;
        inbox.providerOrderId = providerOrderId;
        inbox.receivedAt = now;
        inbox.nextAttemptAt = now;
        return inbox;
    }

    public void processed(Instant now) {
        processedAt = now;
        lastError = null;
    }

    public void retry(Instant now, String code) {
        attempts++;
        lastError = code;
        nextAttemptAt = now.plusSeconds(Math.min(3600, 60L << Math.min(6, attempts)));
    }
}
