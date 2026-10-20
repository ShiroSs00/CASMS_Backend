package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"LocationRecord\"", indexes = {
        @Index(name = "idx_location_record_device_time", columnList = "location_device_id,recorded_at"),
        @Index(name = "idx_location_record_ship_time", columnList = "ship_id,recorded_at")
})
public class LocationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "location_record_id")
    private UUID locationRecordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_device_id", nullable = false)
    private LocationDevice device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", referencedColumnName = "deck_id", insertable = false, updatable = false)
    private Deck deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id", referencedColumnName = "cabin_id", insertable = false, updatable = false)
    private Cabin cabin;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "zone_key", length = 200)
    private String zoneKey;

    @Column(name = "accuracy_meters", precision = 8, scale = 2)
    private BigDecimal accuracyMeters;

    /** Radio measurement only; RSSI is not an absolute passenger location. */
    @Column(name = "rssi_dbm")
    private Integer rssiDbm;

    @Column(name = "estimated_distance_meters", precision = 8, scale = 2)
    private BigDecimal estimatedDistanceMeters;

    @Column(name = "ble_identifier", length = 150)
    private String bleIdentifier;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
