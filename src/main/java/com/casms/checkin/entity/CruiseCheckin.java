package com.casms.checkin.entity;

import com.casms.booking.entity.BookingPassenger;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseCheckin\"")
public class CruiseCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cruise_checkin_id")
    private UUID cruiseCheckinId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id", nullable = false, unique = true)
    private BookingPassenger bookingPassenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkin_status", nullable = false)
    private CruiseCheckinStatus checkinStatus;

    @Type(JsonBinaryType.class)
    @Column(name = "identity_document_snapshot", columnDefinition = "jsonb")
    private String identityDocumentSnapshot;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 255)
    private String emergencyContactPhone;

    @Column(name = "location_tracking_allowed", nullable = false)
    private Boolean locationTrackingAllowed;

    @Column(name = "location_sharing_allowed", nullable = false)
    private Boolean locationSharingAllowed;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "boarding_ready_at")
    private Instant boardingReadyAt;

    @Column(name = "boarding_block_reason", columnDefinition = "text")
    private String boardingBlockReason;
}
