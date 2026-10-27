package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.response.CruiseAuditEventResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseAuditEvent;
import com.casms.ship.repository.CruiseAuditEventRepository;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseAuditService {

    private final CruiseAuditEventRepository auditEventRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;

    /**
     * BE-25: Ghi nhận sự kiện kiểm toán nghiệp vụ (Cruise Business Audit Event)
     * Đảm bảo ghi trong transaction commit, idempotent theo eventIdentity, không ghi "thành công ảo".
     */
    @Transactional
    public CruiseAuditEvent recordEvent(
            Cruise cruise,
            String eventType,
            String eventIdentity,
            UUID actorId,
            String actorRole,
            String requiredPermission,
            String scope,
            String beforeDigest,
            String afterDigest,
            String reason,
            String evidence) {

        if (cruise == null) return null;

        String effectiveEventIdentity = (eventIdentity != null && !eventIdentity.isBlank())
                ? eventIdentity
                : "AUD-" + cruise.getCruiseCode() + "-" + eventType + "-" + System.currentTimeMillis();

        if (auditEventRepository.existsByEventIdentity(effectiveEventIdentity)) {
            log.info("Audit event with identity {} already recorded, skipping duplicate.", effectiveEventIdentity);
            return null;
        }

        String actorUsername = "SYSTEM";
        if (actorId != null) {
            actorUsername = userRepository.findById(actorId)
                    .map(User::getUsername)
                    .orElse(actorId.toString());
        }

        String fingerprint = generateFingerprint(String.format("%s:%s:%s:%s:%s",
                cruise.getCruiseId(), eventType, effectiveEventIdentity, actorUsername, cruise.getRevision()));

        CruiseAuditEvent event = CruiseAuditEvent.builder()
                .cruise(cruise)
                .eventType(eventType)
                .eventIdentity(effectiveEventIdentity)
                .actorId(actorId)
                .actorUsername(actorUsername)
                .actorRole(actorRole)
                .requiredPermission(requiredPermission)
                .scope(scope != null ? scope : "WHOLE_CRUISE")
                .beforeDigest(beforeDigest)
                .afterDigest(afterDigest)
                .reason(reason)
                .evidence(evidence)
                .fingerprint(fingerprint)
                .sourceVersionId(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getVersionId() : null)
                .revision(cruise.getRevision())
                .timestamp(Instant.now())
                .build();

        CruiseAuditEvent saved = auditEventRepository.save(event);
        log.info("Recorded CruiseAuditEvent [{}] for Cruise {}: {}", eventType, cruise.getCruiseCode(), effectiveEventIdentity);
        return saved;
    }

    /**
     * BE-25: Tra cứu lịch sử quyết định và dòng thời gian sự kiện của chuyến đi (Read DTO an toàn)
     */
    @Transactional(readOnly = true)
    public List<CruiseAuditEventResponse> listAuditEvents(UUID cruiseId, String eventType) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise", cruiseId);
        }

        List<CruiseAuditEvent> events;
        if (eventType != null && !eventType.isBlank()) {
            events = auditEventRepository.findByCruise_CruiseIdAndEventTypeOrderByTimestampDesc(cruiseId, eventType.trim().toUpperCase());
        } else {
            events = auditEventRepository.findByCruise_CruiseIdOrderByTimestampDesc(cruiseId);
        }

        return events.stream().map(this::toResponse).toList();
    }

    private CruiseAuditEventResponse toResponse(CruiseAuditEvent e) {
        return CruiseAuditEventResponse.builder()
                .eventId(e.getEventId())
                .cruiseId(e.getCruise().getCruiseId())
                .cruiseCode(e.getCruise().getCruiseCode())
                .eventType(e.getEventType())
                .eventIdentity(e.getEventIdentity())
                .actorUsername(e.getActorUsername())
                .actorRole(e.getActorRole())
                .requiredPermission(e.getRequiredPermission())
                .scope(e.getScope())
                .beforeDigest(e.getBeforeDigest())
                .afterDigest(e.getAfterDigest())
                .reason(e.getReason())
                .evidence(e.getEvidence())
                .fingerprint(e.getFingerprint())
                .sourceVersionId(e.getSourceVersionId())
                .revision(e.getRevision())
                .timestamp(e.getTimestamp())
                .build();
    }

    private String generateFingerprint(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
