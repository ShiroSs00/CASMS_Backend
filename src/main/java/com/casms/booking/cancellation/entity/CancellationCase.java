package com.casms.booking.cancellation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "booking_cancellation_case")
@Getter
@Setter
public class CancellationCase {
    public enum Scenario { CUSTOMER, COMPANY, OPERATIONAL_DISRUPTION }
    public enum Status { PENDING_OPS, PENDING_FINANCE, COMPLETED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "case_id")
    private UUID caseId;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Column(name = "requested_by")
    private UUID requestedBy;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    private String reason;
    @Enumerated(EnumType.STRING)
    private Scenario scenario;
    @Column(name = "issue_id")
    private UUID issueId;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "requested_at")
    private Instant requestedAt;
    @Column(name = "departure_at")
    private Instant departureAt;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_reason")
    private String decisionReason;
    @Version
    private long revision;
}
