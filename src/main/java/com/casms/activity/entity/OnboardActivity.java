package com.casms.activity.entity;

import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Deck;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OnboardActivity\"")
public class OnboardActivity {

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboard_activity_id")
    private UUID onboardActivityId;

    @Column(name = "catalog_entry", nullable = false)
    private boolean catalogEntry;

    @Column(name = "source_activity_id")
    private UUID sourceActivityId;

    @Column(name = "activity_code", nullable = false, unique = true, length = 50)
    private String activityCode;

    @Column(name = "activity_name", nullable = false, length = 200)
    private String activityName;

    /** Phân loại danh mục hoạt động trên tàu (Giải trí, Thể thao, Gia đình, Sự kiện, Lớp học) */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private OnboardActivityCategory category;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "meeting_point", length = 255)
    private String meetingPoint;

    @Column(name = "location", length = 250)
    private String location;

    /** Tầng (deck) nơi hoạt động diễn ra — ví dụ: Tầng 2, Promenade Deck */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id")
    private Deck deck;

    /** Phòng/cabin cụ thể nơi hoạt động diễn ra — ví dụ: phòng Spa 201 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id")
    private Cabin cabin;

    @Column(name = "area_object_id")
    private UUID areaObjectId;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", columnDefinition = "char(3)")
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OnboardActivityStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id")
    private Cruise cruise;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
