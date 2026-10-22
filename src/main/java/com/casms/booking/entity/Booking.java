package com.casms.booking.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.casms.booking.enums.BookingStatus;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.payment.entity.OnboardAccount;
import com.casms.ship.entity.Cruise;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Booking\"")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id")
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    // Circular reference resolved via UUID to avoid JPA cycle
    @Column(name = "representative_booking_passenger_id")
    private UUID representativeBookingPassengerId;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 255)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by_user_id", nullable = false)
    private User bookedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false)
    private BookingStatus bookingStatus;

    @Column(name = "agreed_fare_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal agreedFareAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "required_deposit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requiredDepositAmount;

    @Column(name = "final_payment_due_at")
    private Instant finalPaymentDueAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_onboard_account_id")
    private OnboardAccount settlementOnboardAccount;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
