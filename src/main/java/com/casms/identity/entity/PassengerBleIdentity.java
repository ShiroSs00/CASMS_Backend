package com.casms.identity.entity;

import com.casms.booking.entity.BookingPassenger;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import com.casms.ship.entity.Cruise;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only lifecycle record for a passenger BLE tag assignment on one cruise. */
@Getter
@Setter
@Entity
@Table(name = "\"PassengerBleIdentity\"", uniqueConstraints = {
        @UniqueConstraint(name = "uk_passenger_ble_active_tag", columnNames = {"cruise_id", "active_ble_identifier"}),
        @UniqueConstraint(name = "uk_passenger_ble_active_passenger", columnNames = {"cruise_id", "active_booking_passenger_id"})
}, indexes = {
        @Index(name = "idx_passenger_ble_history", columnList = "cruise_id,booking_passenger_id,assigned_at")
})
public class PassengerBleIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "passenger_ble_identity_id")
    private UUID passengerBleIdentityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_passenger_id", nullable = false)
    private BookingPassenger bookingPassenger;

    @Column(name = "ble_identifier", nullable = false, length = 150)
    private String bleIdentifier;

    /** Populated only for active rows so database uniqueness protects concurrent assignment. */
    @Column(name = "active_ble_identifier", length = 150)
    private String activeBleIdentifier;

    @Column(name = "active_booking_passenger_id")
    private UUID activeBookingPassengerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PassengerBleIdentityStatus status;

    @Column(name = "device_name", length = 80)
    private String deviceName;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
        if (status == PassengerBleIdentityStatus.ACTIVE) {
            activeBleIdentifier = bleIdentifier;
            activeBookingPassengerId = bookingPassenger.getBookingPassengerId();
        }
    }
}
