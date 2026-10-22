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
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuidanceSessionServiceTest {

    @Mock
    private GuidanceSessionRepository sessionRepository;

    @Mock
    private GuidanceSessionParticipantRepository participantRepository;

    @Mock
    private CruiseRepository cruiseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingPassengerRepository bookingPassengerRepository;

    @InjectMocks
    private GuidanceSessionService guidanceSessionService;

    private UUID cruiseId;
    private UUID sessionId;
    private UUID instructorId;
    private Cruise cruise;
    private User instructor;
    private GuidanceSession session;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        instructorId = UUID.randomUUID();

        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-0001");

        instructor = new User();
        instructor.setUserId(instructorId);
        instructor.setUsername("trainer_nam");
        instructor.setStatus(UserStatus.ACTIVE);

        session = new GuidanceSession();
        session.setGuidanceSessionId(sessionId);
        session.setCruise(cruise);
        session.setSessionName("Hướng dẫn an toàn cứu sinh & PCCC");
        session.setLocation("Hội trường tầng 3 - Deck 3 Grand Hall");
        session.setStartAt(Instant.now().plusSeconds(1800));
        session.setEndAt(Instant.now().plusSeconds(3600));
        session.setInstructorUser(instructor);
        session.setStatus(GuidanceSessionStatus.Scheduled);
    }

    @Test
    @DisplayName("Tạo buổi phổ cập thông tin thành công với đầy đủ dữ liệu")
    void createSession_Success() {
        GuidanceSessionCreateRequest req = new GuidanceSessionCreateRequest();
        req.setCruiseId(cruiseId);
        req.setSessionName("Hướng dẫn an toàn cứu sinh & PCCC");
        req.setLocation("Hội trường tầng 3 - Deck 3 Grand Hall");
        req.setStartAt(Instant.now().plusSeconds(1800));
        req.setEndAt(Instant.now().plusSeconds(3600));
        req.setInstructorUserId(instructorId);

        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(sessionRepository.save(any(GuidanceSession.class))).thenReturn(session);

        GuidanceSessionResponse resp = guidanceSessionService.createSession(req);

        assertNotNull(resp);
        assertEquals("Hướng dẫn an toàn cứu sinh & PCCC", resp.getSessionName());
        assertEquals("trainer_nam", resp.getInstructorUsername());
        assertEquals(GuidanceSessionStatus.Scheduled, resp.getStatus());
        verify(sessionRepository).save(any(GuidanceSession.class));
    }

    @Test
    @DisplayName("Ném BusinessException khi thời gian kết thúc trước thời gian bắt đầu")
    void createSession_InvalidTimeRange_ThrowsException() {
        GuidanceSessionCreateRequest req = new GuidanceSessionCreateRequest();
        req.setCruiseId(cruiseId);
        req.setSessionName("Test");
        req.setLocation("Room 1");
        req.setStartAt(Instant.now().plusSeconds(3600));
        req.setEndAt(Instant.now().plusSeconds(1800)); // end < start
        req.setInstructorUserId(instructorId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guidanceSessionService.createSession(req));

        assertEquals("INVALID_TIME_RANGE", ex.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tự động thêm hành khách của chuyến vào buổi phổ cập")
    void autoPopulateParticipants_Success() {
        UUID bpId = UUID.randomUUID();
        BookingPassenger bp = new BookingPassenger();
        bp.setBookingPassengerId(bpId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId)).thenReturn(List.of(bp));
        when(participantRepository.existsByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(sessionId, bpId))
                .thenReturn(false);

        int count = guidanceSessionService.autoPopulateParticipants(sessionId);

        assertEquals(1, count);
        verify(participantRepository).save(any(GuidanceSessionParticipant.class));
    }

    @Test
    @DisplayName("Điểm danh hành khách tham dự thành công")
    void recordAttendance_Success() {
        UUID bpId = UUID.randomUUID();
        Passenger p = new Passenger();
        p.setFullName("Nguyễn Văn An");
        p.setDocumentNumberProtected("001202000001");

        BookingPassenger bp = new BookingPassenger();
        bp.setBookingPassengerId(bpId);
        bp.setPassenger(p);

        GuidanceSessionParticipant participant = new GuidanceSessionParticipant();
        participant.setGuidanceSessionParticipantId(UUID.randomUUID());
        participant.setGuidanceSession(session);
        participant.setBookingPassenger(bp);
        participant.setAttendanceStatus(GuidanceSessionParticipantAttendanceStatus.Registered);

        GuidanceAttendanceRequest req = new GuidanceAttendanceRequest();
        req.setBookingPassengerId(bpId);
        req.setAttendanceStatus(GuidanceSessionParticipantAttendanceStatus.Attended);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(participantRepository.findByGuidanceSession_GuidanceSessionIdAndBookingPassenger_BookingPassengerId(sessionId, bpId))
                .thenReturn(Optional.of(participant));
        when(participantRepository.save(any(GuidanceSessionParticipant.class))).thenReturn(participant);

        GuidanceParticipantResponse resp = guidanceSessionService.recordAttendance(sessionId, req);

        assertNotNull(resp);
        assertEquals(GuidanceSessionParticipantAttendanceStatus.Attended, resp.getAttendanceStatus());
        assertEquals("Nguyễn Văn An", resp.getPassengerFullName());
        verify(participantRepository).save(participant);
    }
}
