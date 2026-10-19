package com.casms.servicepackage.entity;

import com.casms.booking.entity.BookingPassenger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"BookingPassengerPackage\"")
public class BookingPassengerPackage {

    @Id
    @Column(name = "booking_passenger_id")
    private UUID bookingPassengerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "booking_passenger_id")
    private BookingPassenger bookingPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "agreed_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal agreedPrice;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;
}
