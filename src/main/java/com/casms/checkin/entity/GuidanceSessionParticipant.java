package com.casms.checkin.entity;

import com.casms.booking.entity.BookingPassenger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"GuidanceSessionParticipant\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"guidance_session_id", "booking_passenger_id"}))
public class GuidanceSessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "guidance_session_participant_id")
    private UUID guidanceSessionParticipantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guidance_session_id", nullable = false)
    private GuidanceSession guidanceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_passenger_id", nullable = false)
    private BookingPassenger bookingPassenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private GuidanceSessionParticipantAttendanceStatus attendanceStatus;

    @Column(name = "attended_at")
    private Instant attendedAt;
}
