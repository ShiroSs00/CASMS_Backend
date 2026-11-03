package com.casms.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Fare fulfillment and its existing operational usage record commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFareFulfillment(UUID grantId,String operationType,UUID operationId,int quantity,String unitCode) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal actor))
            throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Cần tài khoản thao tác để ghi nhận fulfillment.");
        AuditLog event=new AuditLog();event.setActorUserId(actor.getUserId());event.setAction("FARE_BENEFIT_FULFILLED");
        event.setEntityType(operationType);event.setEntityId(operationId);event.setResult("SUCCESS");
        try {event.setDetail(objectMapper.writeValueAsString(java.util.Map.of("grantId",grantId,"operationId",operationId,
                "operationType",operationType,"fulfilledQuantity",quantity,"usageUnit",unitCode)));}
        catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new com.casms.common.exception.BusinessException("ENTITLEMENT_AUDIT_FAILED","Không thể ghi audit fulfillment; không tiêu thụ quyền lợi.");}
        auditRepository.saveAndFlush(event);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String action, String entityType,
                       UUID entityId, String result, String detail) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setResult(result);
        log.setDetail(detail);
        auditRepository.save(log);
    }
    /** Directory writes and their audit must commit together; directory scope is global OPS, not ship/area assignment. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectoryMutation(String entityType, UUID entityId, String operation) {
        recordDirectoryMutation(entityType, entityId, operation, java.util.Map.of(), java.util.Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDirectoryMutation(String entityType, UUID entityId, String operation,
                                        java.util.Map<String,Object> before, java.util.Map<String,Object> after) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal actor)) {
            throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để cập nhật danh mục.");
        }
        String permission = entityType.toUpperCase(java.util.Locale.ROOT) + "_" + operation;
        if (!actor.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER) || !actor.hasAuthority(permission)) {
            throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "DIRECTORY_MANAGE_FORBIDDEN", "Chỉ Quản lý vận hành có quyền tương ứng được cập nhật danh mục này.");
        }
        AuditLog event = new AuditLog();
        event.setActorUserId(actor.getUserId());
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(operation + "_" + entityType.toUpperCase(java.util.Locale.ROOT));
        event.setResult("SUCCESS");
        java.util.Set<String> changedFields = new java.util.LinkedHashSet<>();
        java.util.Set<String> fields = new java.util.LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        for (String field : fields) if (!java.util.Objects.equals(before.get(field), after.get(field))) changedFields.add(field);
        java.util.Map<String,Object> detail = new java.util.LinkedHashMap<>();
        detail.put("actorUserId", actor.getUserId());
        detail.put("actorRoles", actor.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_")).toList());
        detail.put("recordedAt", java.time.Instant.now());
        detail.put("changedFields", changedFields);
        detail.put("before", before);
        detail.put("after", after);
        try {
            event.setDetail(objectMapper.writeValueAsString(detail));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "DIRECTORY_AUDIT_FAILED", "Không thể lưu audit; cập nhật danh mục phải được hủy.");
        }
        auditRepository.saveAndFlush(event);
    }

}
