package com.casms.trip.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.ItineraryType;
import com.casms.trip.enums.TripStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Trip\"")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "trip_code", nullable = false, unique = true, length = 20)
    private String tripCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "duration_nights", nullable = false)
    private Integer durationNights;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 50)
    private ItineraryType tripType;

    @Column(name = "planned_scope", length = 50)
    private String plannedScope;

    @Column(name = "ship_requirements", columnDefinition = "TEXT")
    private String shipRequirements;

    @Column(name = "candidate_destinations", columnDefinition = "TEXT")
    private String candidateDestinations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TripStatus status = TripStatus.DRAFT;

    @Column(name = "structural_locked_at")
    private Instant structuralLockedAt;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber ASC")
    private List<TripScheduleVersion> versions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (revision == null) {
            revision = 1L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
