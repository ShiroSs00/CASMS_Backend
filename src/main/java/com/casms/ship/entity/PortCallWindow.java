package com.casms.ship.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "\"PortCallWindow\"")
public class PortCallWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "window_id")
    private UUID windowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @Column(name = "window_type", nullable = false)
    private String windowType;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @ElementCollection
    @CollectionTable(
            name = "port_call_window_weekday",
            joinColumns = @JoinColumn(name = "window_id"))
    @Column(name = "weekday", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek = new HashSet<>();

    @Column(name = "local_start_time", nullable = false)
    private LocalTime localStartTime;

    @Column(name = "local_end_time", nullable = false)
    private LocalTime localEndTime;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_reference", nullable = false, length = 2000)
    private String sourceReference;

    @Column(name = "source_effective_date")
    private LocalDate sourceEffectiveDate;

    @Column(name = "overnight_allowed")
    private Boolean overnightAllowed;

    @Column(name = "max_stay_minutes")
    private Integer maxStayMinutes;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus = "INACTIVE";

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    // Evidence of the timezone verified by OPS, not an independent source timezone.
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
