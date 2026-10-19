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
        @UniqueConstraint(name = "uk_location_device_code", columnNames = "device_code")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private LocationDeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LocationDeviceStatus status = LocationDeviceStatus.INACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", referencedColumnName = "deck_id", insertable = false, updatable = false)
    private Deck deck;

    @Column(name = "installed_at")
    private Instant installedAt;

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
