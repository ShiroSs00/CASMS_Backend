package com.casms.ship.entity;

import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Cabin\"",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"ship_id", "cabin_number"}),
           @UniqueConstraint(columnNames = {"cabin_id", "ship_id"})
       })
public class Cabin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cabin_id")
    private UUID cabinId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", referencedColumnName = "deck_id", nullable = false)
    private Deck deck;

    @Column(name = "cabin_number", nullable = false, length = 10)
    private String cabinNumber;

    /** Tên phòng / tên công năng chuyên biệt (ví dụ: Phòng Massage Đá Nóng VIP, Studio Bếp) */
    @Column(name = "room_name", length = 150)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_type", nullable = false)
    private CabinType cabinType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CabinStatus status;

    @Column(name = "is_accessible", nullable = false)
    private Boolean isAccessible = false;

    @Column(name = "minimum_occupancy", nullable = false)
    private Integer minimumOccupancy = 1;

    @Column(name = "bed_count")
    private Integer bedCount;

    @Column(name = "bed_configuration", length = 100)
    private String bedConfiguration;

    @Column(name = "area_square_meters", precision = 8, scale = 2)
    private BigDecimal areaSquareMeters;

    @Column(name = "balcony_area_square_meters", precision = 8, scale = 2)
    private BigDecimal balconyAreaSquareMeters;

    @Column(name = "location_code", length = 30)
    private String locationCode;

    @Column(name = "has_balcony", nullable = false)
    private Boolean hasBalcony = false;

    @Column(name = "has_connecting_door", nullable = false)
    private Boolean hasConnectingDoor = false;

    @Column(name = "is_family_cabin", nullable = false)
    private Boolean isFamilyCabin = false;

    @Column(name = "has_bathtub", nullable = false)
    private Boolean hasBathtub = false;

    @Column(name = "has_separate_living_area", nullable = false)
    private Boolean hasSeparateLivingArea = false;

    @Column(name = "model_position_x")
    private Double modelPositionX;
    @Column(name = "model_position_y")
    private Double modelPositionY;
    @Column(name = "model_position_z")
    private Double modelPositionZ;
    @Column(name = "model_rotation_x")
    private Double modelRotationX;
    @Column(name = "model_rotation_y")
    private Double modelRotationY;
    @Column(name = "model_rotation_z")
    private Double modelRotationZ;
    @Column(name = "model_width")
    private Double modelWidth;
    @Column(name = "model_height")
    private Double modelHeight;
    @Column(name = "model_depth")
    private Double modelDepth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

