package com.casms.ship.entity;

import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryDayRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "day_role", nullable = false, length = 40)
    private ItineraryDayRole dayRole = ItineraryDayRole.REGULAR;

    public ItineraryDayRole getDayRole() {
        if (dayNumber == null || itinerary == null) return dayRole;
        if (itinerary.getStartDate() == null || itinerary.getEndDate() == null) return dayRole;
        return ItineraryDayRole.forDay(dayNumber, Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(itinerary.getStartDate(), itinerary.getEndDate()) + 1));
    }

    @PrePersist
    @PreUpdate
    private void synchronizeDayRole() { dayRole = getDayRole(); }


    @Column(name = "ship_time_zone_id", length = 100)
    private String shipTimeZoneId;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "arrivalItineraryDay", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CruiseStop> arrivalStops = new ArrayList<>();

    @OneToMany(mappedBy = "departureItineraryDay", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CruiseStop> departureStops = new ArrayList<>();

    public void addArrivalStop(CruiseStop stop) {
        arrivalStops.add(stop);
        stop.setArrivalItineraryDay(this);
    }

    public void removeArrivalStop(CruiseStop stop) {
        arrivalStops.remove(stop);
        stop.setArrivalItineraryDay(null);
    }

    public void addDepartureStop(CruiseStop stop) {
        departureStops.add(stop);
        stop.setDepartureItineraryDay(this);
    }

    public void removeDepartureStop(CruiseStop stop) {
        departureStops.remove(stop);
        stop.setDepartureItineraryDay(null);
    }
}