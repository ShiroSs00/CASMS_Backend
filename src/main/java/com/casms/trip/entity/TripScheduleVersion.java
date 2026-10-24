package com.casms.trip.entity;

import com.casms.identity.entity.User;
import com.casms.trip.enums.TripScheduleVersionStatus;
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
@Table(name = "\"TripScheduleVersion\"",
       uniqueConstraints = @UniqueConstraint(name = "uq_trip_version", columnNames = {"trip_id", "version_number"}))
public class TripScheduleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TripScheduleVersionStatus status = TripScheduleVersionStatus.DRAFT;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitter_id")
    private User submitter;

    @Column(name = "submitted_revision")
    private Long submittedRevision;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "source_version_id")
    private UUID sourceVersionId;

    @Column(name = "time_policy_version", length = 50)
    private String timePolicyVersion = "v1.0";

    @Column(name = "planning_policy_snapshot", columnDefinition = "TEXT")
    private String planningPolicySnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "version", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<TripPlanDay> days = new ArrayList<>();

    @OneToMany(mappedBy = "version", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    private List<TripPlanStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "version", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDayNumber ASC, plannedStartTime ASC")
    private List<TripPlanEntry> entries = new ArrayList<>();

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
