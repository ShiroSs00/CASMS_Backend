package com.casms.ship.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"PortCallWindowException\"")
public class PortCallWindowException {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exception_id")
    private UUID exceptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @Column(name = "exception_type", nullable = false)
    private String exceptionType = "CLOSURE";

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_reference", nullable = false, length = 2000)
    private String sourceReference;

    @Column(name = "source_effective_date")
    private LocalDate sourceEffectiveDate;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus = "INACTIVE";

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_time_zone_id")
    private String verifiedTimeZoneId;

    @Version private Long revision;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void create() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
    }
}
