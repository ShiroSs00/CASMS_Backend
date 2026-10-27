package com.casms.ship.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripScheduleVersion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Cruise\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_id", "ship_id"}))
public class Cruise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_id")
    private UUID cruiseId;

    @Column(name = "cruise_code", nullable = false, unique = true, length = 20)
    private String cruiseCode;

    @Column(name = "cruise_name", nullable = false, length = 150)
    private String cruiseName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id")
    private Ship ship;

    @Enumerated(EnumType.STRING)
    @Column(name = "cruise_type", nullable = false)
    private CruiseCruiseType cruiseType;

    @Column(name = "departure_datetime", nullable = false)
    private Instant departureDatetime;

    @Column(name = "return_datetime", nullable = false)
    private Instant returnDatetime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_port_id", nullable = false)
    private Port departurePort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_port_id", nullable = false)
    private Port returnPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_trip_id")
    private Trip sourceTrip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_version_id")
    private TripScheduleVersion sourceVersion;

    @Column(name = "source_version_revision")
    private Long sourceVersionRevision;

    @Version
    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CruiseStatus status;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stopped_by_user_id")
    private User stoppedBy;

    @Column(name = "stop_reason", columnDefinition = "TEXT")
    private String stopReason;

    @Column(name = "stop_evidence", columnDefinition = "TEXT")
    private String stopEvidence;

    @Column(name = "stop_scope", length = 50)
    private String stopScope;

    @Column(name = "stop_event_identity", length = 100)
    private String stopEventIdentity;

    @Column(name = "sale_scope", length = 30)
    private String saleScope;

    @Column(name = "opened_for_sale_at")
    private Instant openedForSaleAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_for_sale_by")
    private User openedForSaleBy;

    @Column(name = "sale_event_identity", length = 100)
    private String saleEventIdentity;

    @Column(name = "actual_departure_datetime")
    private Instant actualDepartureDatetime;

    @Column(name = "actual_return_datetime")
    private Instant actualReturnDatetime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_actor_id")
    private User departureActor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completion_actor_id")
    private User completionActor;

    @Column(name = "closure_notes", columnDefinition = "TEXT")
    private String closureNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
