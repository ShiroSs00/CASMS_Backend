package com.casms.checkin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"PassengerIdentifier\"")
public class PassengerIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "passenger_identifier_id")
    private UUID passengerIdentifierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_checkin_id", nullable = false)
    private CruiseCheckin cruiseCheckin;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false)
    private PassengerIdentifierType identifierType;

    @Column(name = "secure_token_reference", nullable = false, unique = true, length = 255)
    private String secureTokenReference;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PassengerIdentifierStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_identifier_id")
    private PassengerIdentifier previousIdentifier;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_known_area", length = 255)
    private String lastKnownArea;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;
}
