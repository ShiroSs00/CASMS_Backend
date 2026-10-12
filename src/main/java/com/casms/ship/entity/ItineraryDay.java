package com.casms.ship.entity;

import com.casms.ship.enums.ItineraryDayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ItineraryDay\"",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"itinerary_id", "day_number"}),
           @UniqueConstraint(columnNames = {"itinerary_id", "date"}),
           @UniqueConstraint(columnNames = {"itinerary_day_id", "itinerary_id"})
       })
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "itinerary_day_id")
    private UUID itineraryDayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private ItineraryDayType dayType;

    @Column(name = "ship_time_zone_id", length = 100)
    private String shipTimeZoneId;

    @Column(name = "description", length = 500)
    private String description;
}
