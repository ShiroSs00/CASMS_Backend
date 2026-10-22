package com.casms.checkin.repository;

import com.casms.checkin.entity.GuidanceSessionParticipant;
import com.casms.checkin.enums.GuidanceSessionParticipantAttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuidanceSessionParticipantRepository extends JpaRepository<GuidanceSessionParticipant, UUID> {

    List<GuidanceSessionParticipant> findByGuidanceSession_GuidanceSessionId(UUID sessionId);

    Optional<GuidanceSessionParticipant> findByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(
            UUID sessionId, UUID bookingPassengerId);

    boolean existsByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(
            UUID sessionId, UUID bookingPassengerId);

    long countByGuidanceSession_GuidanceSessionId(UUID sessionId);

    long countByGuidanceSession_GuidanceSessionIdAndAttendanceStatus(
            UUID sessionId, GuidanceSessionParticipantAttendanceStatus attendanceStatus);
}
