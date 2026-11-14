package com.casms.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"PaymentWebhookEvent\"")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "webhook_event_id")
    private UUID webhookEventId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "external_event_id", nullable = false, unique = true, length = 255)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus; // RECEIVED, PROCESSED, IGNORED, FAILED

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}

