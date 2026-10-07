package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseStop\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"itinerary_id", "stop_order"}))
public class CruiseStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_stop_id")
    private UUID cruiseStopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrival_itinerary_day_id", nullable = false)
    private ItineraryDay arrivalItineraryDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_itinerary_day_id", nullable = false)
    private ItineraryDay departureItineraryDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "planned_arrival", nullable = false)
    private Instant plannedArrival;

    @Column(name = "planned_departure", nullable = false)
    private Instant plannedDeparture;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "actual_departure")
    private Instant actualDeparture;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false)
    private CruiseStopCallType callType;

    @Enumerated(EnumType.STRING)
    @Column(name = "arrival_method")
    private CruiseStopArrivalMethod arrivalMethod;

    @Column(name = "all_aboard_time")
    private Instant allAboardTime;

    @Column(name = "tender_transfer_minutes")
    private Integer tenderTransferMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CruiseStopStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substituted_by_stop_id")
    private CruiseStop substitutedByStop;

    @Column(name = "port_contact_name", length = 200)
    private String portContactName;

    @Column(name = "port_contact_role", length = 100)
    private String portContactRole;

    @Column(name = "port_contact_phone", length = 50)
    private String portContactPhone;

    @Column(name = "port_contact_email", length = 255)
    private String portContactEmail;

    @Column(name = "port_contact_responsibility", length = 500)
    private String portContactResponsibility;

    @Column(name = "notes", length = 1000)
    private String notes;
}

