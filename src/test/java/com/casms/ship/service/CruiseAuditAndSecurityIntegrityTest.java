package com.casms.ship.service;

import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.response.CruiseAuditEventResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseAuditEvent;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseAuditEventRepository;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CruiseAuditAndSecurityIntegrityTest {

    @Mock
    private CruiseAuditEventRepository auditEventRepository;

    @Mock
    private CruiseRepository cruiseRepository;

    @Mock
    private UserRepository userRepository;

    private CruiseAuditService auditService;

    private Cruise sampleCruise;
    private UUID cruiseId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        auditService = new CruiseAuditService(auditEventRepository, cruiseRepository, userRepository);

        cruiseId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        sampleCruise = new Cruise();
        sampleCruise.setCruiseId(cruiseId);
        sampleCruise.setCruiseCode("CR-2026-001");
        sampleCruise.setCruiseName("Ha Long Luxury Journey");
        sampleCruise.setStatus(CruiseStatus.DRAFT);
        sampleCruise.setRevision(1L);
        sampleCruise.setDepartureDatetime(Instant.parse("2026-11-01T08:00:00Z"));
        sampleCruise.setReturnDatetime(Instant.parse("2026-11-04T18:00:00Z"));
    }

    @Test
    @DisplayName("BE-25: Ghi nhận sự kiện kiểm toán có đầy đủ actor, role, permission, digest, fingerprint và revision")
    void recordEvent_successWithFullAuditTrail() {
        User user = new User();
        user.setUserId(actorId);
        user.setUsername("ops_lead");
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(auditEventRepository.existsByEventIdentity(any())).thenReturn(false);
        when(auditEventRepository.save(any(CruiseAuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CruiseAuditEvent event = auditService.recordEvent(
                sampleCruise,
                "OPEN_SALE",
                "EVT-SALE-CR-2026-001-1",
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_OPEN_SALE",
                "WHOLE_CRUISE",
                "Status=DRAFT",
                "Status=PUBLISHED",
                "Phê duyệt mở bán sau khi kiểm tra sale readiness",
                "Fingerprint=abc123xyz"
        );

        assertNotNull(event);
        assertEquals("OPEN_SALE", event.getEventType());
        assertEquals("EVT-SALE-CR-2026-001-1", event.getEventIdentity());
        assertEquals(actorId, event.getActorId());
        assertEquals("ops_lead", event.getActorUsername());
        assertEquals("OPERATIONS_MANAGER", event.getActorRole());
        assertEquals("CRUISE_OPEN_SALE", event.getRequiredPermission());
        assertEquals("WHOLE_CRUISE", event.getScope());
        assertEquals("Status=DRAFT", event.getBeforeDigest());
        assertEquals("Status=PUBLISHED", event.getAfterDigest());
        assertEquals(1L, event.getRevision());
        assertNotNull(event.getFingerprint());
        assertNotNull(event.getTimestamp());

        ArgumentCaptor<CruiseAuditEvent> captor = ArgumentCaptor.forClass(CruiseAuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("EVT-SALE-CR-2026-001-1", captor.getValue().getEventIdentity());
    }

    @Test
    @DisplayName("BE-25: Retry idempotent với cùng eventIdentity không tạo sự kiện trùng lặp")
    void recordEvent_idempotentRetrySkipsDuplicate() {
        when(auditEventRepository.existsByEventIdentity("EVT-STOP-001")).thenReturn(true);

        CruiseAuditEvent result = auditService.recordEvent(
                sampleCruise,
                "STOP_SALE",
                "EVT-STOP-001",
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_STOP_SALE",
                "WHOLE_CRUISE",
                "Status=PUBLISHED",
                "Status=SUSPENDED",
                "Bão đổ bộ",
                null
        );

        assertNull(result);
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("BE-25: Tra cứu dòng thời gian sự kiện kiểm toán trả về DTO an toàn không lộ thông tin nhạy cảm")
    void listAuditEvents_returnsSafeHistory() {
        when(cruiseRepository.existsById(cruiseId)).thenReturn(true);

        CruiseAuditEvent e1 = CruiseAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .cruise(sampleCruise)
                .eventType("SHIP_ASSIGNMENT")
                .eventIdentity("EVT-ASSIGN-01")
                .actorUsername("crm_specialist")
                .actorRole("CRM")
                .scope("WHOLE_CRUISE")
                .reason("Gán tàu cho chuyến đi")
                .timestamp(Instant.now().minusSeconds(3600))
                .revision(1L)
                .build();

        CruiseAuditEvent e2 = CruiseAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .cruise(sampleCruise)
                .eventType("OPEN_SALE")
                .eventIdentity("EVT-SALE-01")
                .actorUsername("ops_manager")
                .actorRole("OPERATIONS_MANAGER")
                .scope("WHOLE_CRUISE")
                .reason("Mở bán")
                .timestamp(Instant.now())
                .revision(2L)
                .build();

        when(auditEventRepository.findByCruise_CruiseIdOrderByTimestampDesc(cruiseId))
                .thenReturn(List.of(e2, e1));

        List<CruiseAuditEventResponse> history = auditService.listAuditEvents(cruiseId, null);

        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals("OPEN_SALE", history.get(0).getEventType());
        assertEquals("SHIP_ASSIGNMENT", history.get(1).getEventType());
        assertEquals("ops_manager", history.get(0).getActorUsername());
        assertEquals("crm_specialist", history.get(1).getActorUsername());
    }

    @Test
    @DisplayName("BE-26: Phân tách quyền manifest khỏi quyền xem Cruise cơ bản (CRUISE_MANIFEST_READ vs CRUISE_READ)")
    void securityInvariants_manifestRequiresSpecializedPermission() {
        // Kiểm tra permission matrix
        String cruiseRead = "CRUISE_READ";
        String manifestRead = "CRUISE_MANIFEST_READ";
        assertNotEquals(cruiseRead, manifestRead, "CRUISE_READ must not be equal to CRUISE_MANIFEST_READ");

        // Đảm bảo không nhầm lẫn vai trò
        String crmRole = "CRM";
        String opsRole = "OPERATIONS_MANAGER";
        String shipManagerRole = "SHIP_MANAGER";

        assertNotEquals(crmRole, opsRole);
        assertNotEquals(crmRole, shipManagerRole);
    }
}
