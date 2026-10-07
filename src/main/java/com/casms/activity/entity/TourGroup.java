package com.casms.activity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourGroup\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"shore_excursion_id", "group_code"}))
public class TourGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tour_group_id")
    private UUID tourGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shore_excursion_id", nullable = false)
    private ShoreExcursion shoreExcursion;

    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;

    @Column(name = "maximum_capacity", nullable = false)
    private Integer maximumCapacity;

    // guide_id and vehicle_id: unresolved external references, stored as UUID only
    @Column(name = "guide_id")
    private UUID guideId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "meeting_point", length = 250)
    private String meetingPoint;

    @Column(name = "assembly_time")
    private Instant assemblyTime;

    @Column(name = "planned_departure_time")
    private Instant plannedDepartureTime;

    @Column(name = "planned_return_time")
    private Instant plannedReturnTime;

    @Column(name = "actual_departure_time")
    private Instant actualDepartureTime;

    @Column(name = "actual_return_time")
    private Instant actualReturnTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TourGroupStatus status;

    @Column(name = "notes", length = 500)
    private String notes;
}
