package com.casms.notification.entity;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.identity.entity.User;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseImpactTask;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "\"ImpactNotificationOutbox\"",
       uniqueConstraints = @UniqueConstraint(name = "uk_impact_notification_outbox_event_booking_type",
               columnNames = {"event_identity", "booking_id", "notification_type"}))
public class ImpactNotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_id")
    private UUID outboxId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private CruiseImpactTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "event_identity", nullable = false, length = 100)
    private String eventIdentity;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "delivery_channel", length = 30)
    private String deliveryChannel = "IN_APP";

    @Builder.Default
    @Column(name = "delivery_semantics", length = 255)
    private String deliverySemantics = "IN_APP_DELIVERED (Thông báo gửi vào hộp thư trong ứng dụng)";

    @Column(name = "in_app_delivered_at")
    private Instant inAppDeliveredAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
