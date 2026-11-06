package com.casms.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            String result,
            String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setResult(result);
        auditLog.setDetail(detail);
        auditRepository.save(auditLog);
    }

    /**
     * Directory writes and their audit must commit together; directory scope is global OPS, not
     * ship/area assignment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectoryMutation(String entityType, UUID entityId, String operation) {
        doRecordDirectoryMutation(
                entityType, entityId, operation, Map.of(), Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectoryMutation(
            String entityType,
            UUID entityId,
            String operation,
            Map<String, Object> before,
            Map<String, Object> after) {
        doRecordDirectoryMutation(entityType, entityId, operation, before, after);
    }

    private void doRecordDirectoryMutation(
            String entityType,
            UUID entityId,
            String operation,
            Map<String, Object> before,
            Map<String, Object> after) {
        var authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof com.casms.security.principal.CasmsPrincipal actor)) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Yêu cầu đăng nhập để cập nhật danh mục.");
        }
        String permission =
                (entityType.startsWith("PortCallWindow")
                                ? "PORT_CALL_WINDOW"
                                : entityType.toUpperCase(Locale.ROOT))
                        + "_"
                        + operation;
        if (!actor.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER)
                || !actor.hasAuthority(permission)) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "DIRECTORY_MANAGE_FORBIDDEN",
                    "Chỉ Quản lý vận hành có quyền tương ứng được cập nhật danh mục này.");
        }
        AuditLog event = new AuditLog();
        event.setActorUserId(actor.getUserId());
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(operation + "_" + entityType.toUpperCase(Locale.ROOT));
        event.setResult("SUCCESS");

        Set<String> changedFields = Stream.concat(before.keySet().stream(), after.keySet().stream())
                .filter(field -> !Objects.equals(before.get(field), after.get(field)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actorUserId", actor.getUserId());
        detail.put(
                "actorRoles",
                actor.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(a -> a.startsWith("ROLE_"))
                        .toList());
        detail.put("recordedAt", Instant.now());
        detail.put("changedFields", changedFields);
        detail.put("before", before);
        detail.put("after", after);
        try {
            event.setDetail(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "DIRECTORY_AUDIT_FAILED",
                    "Không thể lưu audit; cập nhật danh mục phải được hủy.");
        }
        auditRepository.saveAndFlush(event);
    }

    /**
     * Current-policy evidence is appended without rewriting the materialized schedule or review
     * fingerprint.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPortWindowEvaluation(
            UUID cruiseId, List<com.casms.ship.dto.PortWindowContract.Check> checks) {
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (auth == null
                || !(auth.getPrincipal()
                        instanceof com.casms.security.principal.CasmsPrincipal actor)) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Yêu cầu đăng nhập.");
        }
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actor.getUserId());
        auditLog.setEntityType("Cruise");
        auditLog.setEntityId(cruiseId);
        auditLog.setAction("PORT_WINDOW_REVALIDATE");
        auditLog.setResult("SUCCESS");
        try {
            auditLog.setDetail(objectMapper.writeValueAsString(checks));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot capture Port window evidence", ex);
        }
        auditRepository.saveAndFlush(auditLog);
    }
}
