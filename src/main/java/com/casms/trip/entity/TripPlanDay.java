package com.casms.trip.entity;

import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.entity.Port;
import java.time.LocalTime;
import com.casms.ship.enums.ItineraryDayRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TripPlanDay\"",
       uniqueConstraints = @UniqueConstraint(name = "uq_version_day_number", columnNames = {"version_id", "day_number"}))
public class TripPlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "day_id")
    private UUID dayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private TripScheduleVersion version;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", length = 50)
    private ItineraryDayType dayType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_port_id")
    private Port departurePort;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_role", nullable = false, length = 40)
    private ItineraryDayRole dayRole = ItineraryDayRole.REGULAR;

    public ItineraryDayRole getDayRole() {
        if (dayNumber == null || version == null) return dayRole;
        if (version.getTrip() == null || version.getTrip().getDurationDays() == null) return dayRole;
        return ItineraryDayRole.forDay(dayNumber, version.getTrip().getDurationDays());
    }

    @PrePersist
    @PreUpdate
    private void synchronizeDayRole() { dayRole = getDayRole(); }


    @Column(name = "ship_time_zone_id", nullable = false, length = 100)
    private String shipTimeZoneId = "Asia/Ho_Chi_Minh";

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "day", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDayNumber ASC, plannedStartTime ASC")
    private List<TripPlanEntry> entries = new ArrayList<>();
}
