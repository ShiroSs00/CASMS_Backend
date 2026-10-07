package com.casms.checkin.entity;

import com.casms.ship.entity.CruiseStop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"GangwayEvent\"")
public class GangwayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "gangway_event_id")
    private UUID gangwayEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_identifier_id", nullable = false)
    private PassengerIdentifier passengerIdentifier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_stop_id")
    private CruiseStop cruiseStop;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private GangwayEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private GangwayEventResult result;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;
}
