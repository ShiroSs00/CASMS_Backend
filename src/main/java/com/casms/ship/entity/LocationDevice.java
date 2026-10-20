package com.casms.ship.entity;

import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"LocationDevice\"", uniqueConstraints = {
        @UniqueConstraint(name = "uk_location_device_code", columnNames = "device_code"),
        @UniqueConstraint(name = "uk_location_device_ship_ble_identifier", columnNames = {"ship_id", "ble_identifier"})
}, indexes = {
        @Index(name = "idx_location_device_ship_status", columnList = "ship_id,status")
})
public class LocationDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "location_device_id")
    private UUID locationDeviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    /** Hardware BLE address/UUID when the configured device has one. */
    @Column(name = "ble_identifier", length = 150)
    private String bleIdentifier;

    @Column(name = "model_node_key", length = 200)
    private String modelNodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private LocationDeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LocationDeviceStatus status = LocationDeviceStatus.INACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", referencedColumnName = "deck_id")
    private Deck deck;

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
    @Column(name = "tx_power_dbm")
    private Double txPowerDbm = -59.0;
    @Column(name = "path_loss_exponent")
    private Double pathLossExponent = 2.2;
    @Column(name = "model_units_per_meter")
    private Double modelUnitsPerMeter = 1.0;

    @Column(name = "calibrated", nullable = false)
    private Boolean calibrated = false;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "installed_by", length = 100)
    private String installedBy;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
