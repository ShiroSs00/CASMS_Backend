package com.casms.checkin.entity;

import com.casms.booking.entity.BookingPassenger;
import com.casms.checkin.enums.CruiseCheckoutStatus;
import com.casms.identity.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseCheckout\"")
public class CruiseCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_checkout_id")
    private UUID cruiseCheckoutId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id", nullable = false, unique = true)
    private BookingPassenger bookingPassenger;

    // FK to FinalSettlement - using UUID to avoid circular module dependency
    @Column(name = "final_settlement_id")
    private UUID finalSettlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_status", nullable = false)
    private CruiseCheckoutStatus checkoutStatus;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "cabin_released_at")
    private Instant cabinReleasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_verified_by_user_id")
    private User exitVerifiedByUser;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "block_reason", columnDefinition = "text")
    private String blockReason;
}
