package com.casms.trip.entity;

import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TripPlanStop\"",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_version_stop_client_key", columnNames = {"version_id", "client_key"}),
           @UniqueConstraint(name = "uq_version_stop_order", columnNames = {"version_id", "stop_order"})
       })
public class TripPlanStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stop_id")
    private UUID stopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private TripScheduleVersion version;

    @Column(name = "client_key", nullable = false, length = 100)
    private String clientKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "arrival_day_number", nullable = false)
    private Integer arrivalDayNumber;

    @Column(name = "planned_arrival_time")
    private LocalTime plannedArrivalTime;

    @Column(name = "departure_day_number", nullable = false)
    private Integer departureDayNumber;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", length = 50)
    private CruiseStopCallType callType = CruiseStopCallType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "arrival_method", length = 50)
    private CruiseStopArrivalMethod arrivalMethod = CruiseStopArrivalMethod.BERTH;

    @Column(name = "all_aboard_day_number")
    private Integer allAboardDayNumber;

    @Column(name = "all_aboard_time")
    private LocalTime allAboardTime;

    @Column(name = "is_embarkation", nullable = false)
    private Boolean isEmbarkation = false;

    @Column(name = "is_disembarkation", nullable = false)
    private Boolean isDisembarkation = false;

    @OneToMany(mappedBy = "stop", fetch = FetchType.LAZY)
    private List<TripPlanEntry> shoreEntries = new ArrayList<>();
}
