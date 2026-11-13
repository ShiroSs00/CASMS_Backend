package com.casms.booking.entity;

import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.CabinSelectionMode;
import com.casms.identity.entity.Passenger;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "\"BookingPassenger\"",
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

    // NULL mode/Fare fields preserve legacy records. Category holds use Booking's existing lifecycle/expiry.
    @Enumerated(EnumType.STRING)
    @Column(name="cabin_selection_mode",length=20)
    private CabinSelectionMode cabinSelectionMode;
    @Column(name="cabin_group_key",length=100) private String cabinGroupKey;
    @Column(name="fare_offer_id") private UUID fareOfferId;
    @Column(name="fare_price_revision_id") private UUID farePriceRevisionId;
    @Column(name="fare_template_version_id") private UUID fareTemplateVersionId;
    @Column(name="fare_cabin_category",length=30) private String fareCabinCategory;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="fare_selection_payload",columnDefinition="jsonb") private String fareSelectionPayload;
}
