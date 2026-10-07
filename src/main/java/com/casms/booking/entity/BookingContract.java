package com.casms.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"BookingContract\"")
public class BookingContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_contract_id")
    private UUID bookingContractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "contract_reference", nullable = false, unique = true, length = 255)
    private String contractReference;

    @Column(name = "document_reference", nullable = false, length = 255)
    private String documentReference;

    // Composite FK (accepted_by_booking_passenger_id, booking_id) simplified to UUID
    @Column(name = "accepted_by_booking_passenger_id")
    private UUID acceptedByBookingPassengerId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingContractStatus status;
}
