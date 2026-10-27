package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "\"CruiseAuditEvent\"")
public class CruiseAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_identity", nullable = false, length = 100)
    private String eventIdentity;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(name = "required_permission", length = 100)
    private String requiredPermission;

    @Builder.Default
    @Column(name = "scope", nullable = false, length = 50)
    private String scope = "WHOLE_CRUISE";

    @Column(name = "before_digest", columnDefinition = "TEXT")
    private String beforeDigest;

    @Column(name = "after_digest", columnDefinition = "TEXT")
    private String afterDigest;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "fingerprint", length = 100)
    private String fingerprint;

    @Column(name = "source_version_id")
    private UUID sourceVersionId;

    @Column(name = "revision")
    private Long revision;

    @Builder.Default
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();
}
