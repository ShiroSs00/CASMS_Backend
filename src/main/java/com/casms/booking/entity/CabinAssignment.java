package com.casms.booking.entity;

import com.casms.identity.entity.User;
import com.casms.ship.entity.Cabin;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CabinAssignment\"")
public class CabinAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cabin_assignment_id")
    private UUID cabinAssignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id", nullable = false)
    private BookingPassenger bookingPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id", nullable = false)
    private Cabin cabin;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    private User assignedByUser;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;
}
