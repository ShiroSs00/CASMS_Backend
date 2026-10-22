package com.casms.checkin.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.dto.request.GuidanceAttendanceRequest;
import com.casms.checkin.dto.request.GuidanceSessionCreateRequest;
import com.casms.checkin.dto.response.GuidanceParticipantResponse;
import com.casms.checkin.dto.response.GuidanceSessionResponse;
import com.casms.checkin.entity.GuidanceSession;
import com.casms.checkin.entity.GuidanceSessionParticipant;
import com.casms.checkin.enums.GuidanceSessionParticipantAttendanceStatus;
import com.casms.checkin.enums.GuidanceSessionStatus;
import com.casms.checkin.repository.GuidanceSessionParticipantRepository;
import com.casms.checkin.repository.GuidanceSessionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuidanceSessionService {

    private final GuidanceSessionRepository sessionRepository;
    private final GuidanceSessionParticipantRepository participantRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;
    private final BookingPassengerRepository bookingPassengerRepository;

    @Transactional
    public GuidanceSessionResponse createSession(GuidanceSessionCreateRequest request) {
        log.info("Creating guidance session '{}' for cruise {}", request.getSessionName(), request.getCruiseId());

        if (request.getStartAt() == null || request.getEndAt() == null || !request.getEndAt().isAfter(request.getStartAt())) {
            throw new BusinessException("INVALID_TIME_RANGE", "Session end time must be strictly after start time");
        }

        Cruise cruise = cruiseRepository.findById(request.getCruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + request.getCruiseId()));

        User instructor = userRepository.findById(request.getInstructorUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor user not found with id: " + request.getInstructorUserId()));

        if (instructor.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("INACTIVE_USER", "Cannot assign inactive user as instructor");
        }

        GuidanceSession session = new GuidanceSession();
        session.setCruise(cruise);
        session.setSessionName(request.getSessionName().trim());
        session.setLocation(request.getLocation().trim());
        session.setStartAt(request.getStartAt());
        session.setEndAt(request.getEndAt());
        session.setInstructorUser(instructor);
        session.setStatus(GuidanceSessionStatus.Scheduled);

        GuidanceSession saved = sessionRepository.save(session);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GuidanceSessionResponse> listByCruise(UUID cruiseId) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise not found with id: " + cruiseId);
        }
        return sessionRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuidanceSessionResponse getDetail(UUID sessionId) {
        GuidanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Guidance session not found with id: " + sessionId));
        return toResponse(session);
    }

    @Transactional
    public GuidanceSessionResponse updateStatus(UUID sessionId, GuidanceSessionStatus status) {
        GuidanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Guidance session not found with id: " + sessionId));
        session.setStatus(status);
        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public int autoPopulateParticipants(UUID sessionId) {
        GuidanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Guidance session not found with id: " + sessionId));

        UUID cruiseId = session.getCruise().getCruiseId();
        List<BookingPassenger> passengers = bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId);
        int addedCount = 0;

        for (BookingPassenger bp : passengers) {
            if (!participantRepository.existsByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(
                    sessionId, bp.getBookingPassengerId())) {
                GuidanceSessionParticipant participant = new GuidanceSessionParticipant();
                participant.setGuidanceSession(session);
                participant.setBookingPassenger(bp);
                participant.setAttendanceStatus(GuidanceSessionParticipantAttendanceStatus.Registered);
                participantRepository.save(participant);
                addedCount++;
            }
        }

        log.info("Auto-populated {} participants for guidance session {}", addedCount, sessionId);
        return addedCount;
    }

    @Transactional
    public GuidanceParticipantResponse recordAttendance(UUID sessionId, GuidanceAttendanceRequest request) {
        GuidanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Guidance session not found with id: " + sessionId));

        GuidanceSessionParticipant participant = participantRepository
                .findByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(
                        sessionId, request.getBookingPassengerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Participant not found with passenger id %s in session %s",
                                request.getBookingPassengerId(), sessionId)));

        participant.setAttendanceStatus(request.getAttendanceStatus());
        if (request.getAttendanceStatus() == GuidanceSessionParticipantAttendanceStatus.Attended) {
            participant.setAttendedAt(Instant.now());
        }

        GuidanceSessionParticipant saved = participantRepository.save(participant);
        return toParticipantResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GuidanceParticipantResponse> listParticipants(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Guidance session not found with id: " + sessionId);
        }
        return participantRepository.findByGuidanceSession_GuidanceSessionId(sessionId).stream()
                .map(this::toParticipantResponse)
                .toList();
    }

    private GuidanceSessionResponse toResponse(GuidanceSession s) {
        long total = participantRepository.countByGuidanceSession_GuidanceSessionId(s.getGuidanceSessionId());
        long attended = participantRepository.countByGuidanceSession_GuidanceSessionIdAndAttendanceStatus(
                s.getGuidanceSessionId(), GuidanceSessionParticipantAttendanceStatus.Attended);

        return GuidanceSessionResponse.builder()
                .guidanceSessionId(s.getGuidanceSessionId())
                .cruiseId(s.getCruise().getCruiseId())
                .cruiseCode(s.getCruise().getCruiseCode())
                .sessionName(s.getSessionName())
                .location(s.getLocation())
                .startAt(s.getStartAt())
                .endAt(s.getEndAt())
                .instructorUserId(s.getInstructorUser().getUserId())
                .instructorUsername(s.getInstructorUser().getUsername())
                .status(s.getStatus())
                .totalParticipants(total)
                .attendedCount(attended)
                .build();
    }

    private GuidanceParticipantResponse toParticipantResponse(GuidanceSessionParticipant p) {
        var passenger = p.getBookingPassenger() != null ? p.getBookingPassenger().getPassenger() : null;
        String name = passenger != null ? passenger.getFullName() : "N/A";
        String idNum = passenger != null ? passenger.getDocumentNumberProtected() : "N/A";

        return GuidanceParticipantResponse.builder()
                .guidanceSessionParticipantId(p.getGuidanceSessionParticipantId())
                .sessionId(p.getGuidanceSession().getGuidanceSessionId())
                .bookingPassengerId(p.getBookingPassenger().getBookingPassengerId())
                .passengerFullName(name)
                .identificationNumber(idNum)
                .attendanceStatus(p.getAttendanceStatus())
                .attendedAt(p.getAttendedAt())
                .build();
    }
}
