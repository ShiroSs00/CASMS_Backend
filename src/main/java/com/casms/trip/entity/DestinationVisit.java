package com.casms.trip.entity;

import com.casms.ship.entity.Destination;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "\"DestinationVisit\"",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stop_id", "client_key"}))
public class DestinationVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "visit_id")
    private UUID visitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private TripPlanStop stop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "client_key", nullable = false, length = 100)
    private String clientKey;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "destination_name_snapshot")
    private String destinationNameSnapshot;
}
