package com.casms.trip.entity;

import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Destination;
import com.casms.trip.enums.TripPlanEntryKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TripPlanEntry\"",
       uniqueConstraints = @UniqueConstraint(name = "uq_version_entry_client_key", columnNames = {"version_id", "client_key"}))
public class TripPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id")
    private UUID entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private TripScheduleVersion version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private TripPlanDay day;

    @Column(name = "client_key", nullable = false, length = 100)
    private String clientKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 50)
    private TripPlanEntryKind kind;

    /** Phân loại danh mục hoạt động trên tàu (Giải trí, Thể thao, Gia đình, Sự kiện, Lớp học) */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private com.casms.activity.enums.OnboardActivityCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id")
    private TripPlanStop stop;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "source_activity_id")
    private UUID sourceActivityId;

    @Column(name = "source_excursion_id")
    private UUID sourceExcursionId;

    @Column(name = "series_client_key", length = 100)
    private String seriesClientKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_day_number", nullable = false)
    private Integer startDayNumber;

    @Column(name = "planned_start_time", nullable = false)
    private LocalTime plannedStartTime;

    @Column(name = "end_day_number", nullable = false)
    private Integer endDayNumber;

    @Column(name = "planned_end_time", nullable = false)
    private LocalTime plannedEndTime;

    @Column(name = "time_zone_id", length = 100)
    private String timeZoneId;

    @Column(name = "location_text", length = 255)
    private String locationText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id")
    private Cabin cabin;

    @Column(name = "meeting_point", length = 255)
    private String meetingPoint;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
