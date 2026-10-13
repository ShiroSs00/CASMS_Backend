package com.casms.activity.entity;

import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.identity.entity.Passenger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourGroupPassenger\"")
public class TourGroupPassenger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tour_group_passenger_id")
    private UUID tourGroupPassengerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_group_id", nullable = false)
    private TourGroup tourGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false)
    private TourGroupPassengerAssignmentStatus assignmentStatus;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}