package com.casms.ship.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseDestinationVisit\"")
public class CruiseDestinationVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "visit_id")
    private UUID visitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_stop_id", nullable = false)
    private CruiseStop stop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "source_visit_id", nullable = false)
    private UUID sourceVisitId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "destination_name_snapshot", nullable = false)
    private String destinationNameSnapshot;
}
