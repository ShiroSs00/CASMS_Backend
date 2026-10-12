package com.casms.booking.entity;

import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.identity.entity.Passenger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"BookingPassenger\"",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"booking_id", "passenger_id"}),
           @UniqueConstraint(columnNames = {"booking_passenger_id", "booking_id"})
       })
public class BookingPassenger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_passenger_id")
    private UUID bookingPassengerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_status", nullable = false)
    private BookingPassengerParticipationStatus participationStatus;

    @Column(name = "requested_cabin_category", length = 255)
    private String requestedCabinCategory;
}
