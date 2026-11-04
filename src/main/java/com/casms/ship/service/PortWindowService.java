package com.casms.ship.service;

import com.casms.audit.AuditService;
import com.casms.common.exception.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.entity.*;
import com.casms.ship.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortWindowService {
    private final PortRepository ports;
    private final PortCallWindowRepository windows;
    private final PortCallWindowExceptionRepository closures;
    private final AuditService audit;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final com.casms.audit.AuditRepository auditRepository;

    public org.springframework.data.domain.Page<com.casms.audit.AuditLog> history(
            UUID portId,
            UUID id,
            boolean exception,
            org.springframework.data.domain.Pageable pageable) {
        if (exception) closure(portId, id);
        else window(portId, id);
        return auditRepository.findByEntityIdOrderByOccurredAtDesc(id, pageable);
    }

    private Port port(UUID id) {
        return ports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Port", id));
    }

    public Port lock(UUID id) {
        Port current =
                ports.lockDirectory(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Port", id));
        entityManager.refresh(current);
        return current;
    }

    public Directory read(UUID id) {
        Port p = port(id);
        return new Directory(
                id,
                p.getTimeZoneId(),
                windows.findByPortPortIdOrderByValidFromAsc(id).stream().map(this::view).toList(),
                closures.findByPortPortIdOrderByValidFromAsc(id).stream().map(this::view).toList());
    }

    private void revision(Long expected, Long actual) {
        if (expected == null || !expected.equals(actual))
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "STALE_REVISION",
                    "Dữ liệu khung giờ cảng đã thay đổi. Tải lại trước khi cập nhật.");
    }

    private void dates(LocalDate from, LocalDate to, LocalDate source) {
        if (to != null && to.isBefore(from) || source != null && to != null && source.isAfter(to))
            throw new BusinessException(
                    "INVALID_WINDOW_DATES",
                    "Khoảng hiệu lực hoặc ngày hiệu lực nguồn không hợp lệ.");
    }

    private CasmsPrincipal actor() {
        var a =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof CasmsPrincipal))
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập.");
        return (CasmsPrincipal) a.getPrincipal();
    }

    private PortCallWindow window(UUID portId, UUID id) {
        var w =
                windows.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("PortCallWindow", id));
        if (!w.getPort().getPortId().equals(portId))
            throw new ResourceNotFoundException("PortCallWindow", id);
        return w;
    }

    private PortCallWindowException closure(UUID portId, UUID id) {
        var c =
                closures.findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("PortCallWindowException", id));
        if (!c.getPort().getPortId().equals(portId))
            throw new ResourceNotFoundException("PortCallWindowException", id);
        return c;
    }

    private Map<String, Object> snap(Object value) {
        return Collections.singletonMap("data", value);
    }

    @Transactional
    public Window write(UUID portId, UUID id, WindowWrite request) {
        Port p = lock(portId);
        PortCallWindow w = id == null ? new PortCallWindow() : window(portId, id);
        Object before = id == null ? null : view(w);
        if (id != null) revision(request.expectedRevision(), w.getRevision());
        dates(request.validFrom(), request.validTo(), request.sourceEffectiveDate());
        if (!request.localStartTime().isBefore(request.localEndTime()))
            throw new BusinessException(
                    "INVALID_WINDOW_HOURS",
                    "Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày. Chưa có contract cho khung"
                        + " giờ qua nửa đêm.");
        w.setPort(p);
        w.setWindowType(request.windowType());
        w.setValidFrom(request.validFrom());
        w.setValidTo(request.validTo());
        w.setDaysOfWeek(new HashSet<>(request.daysOfWeek()));
        w.setLocalStartTime(request.localStartTime());
        w.setLocalEndTime(request.localEndTime());
        w.setSourceType(request.sourceType());
        w.setSourceReference(request.sourceReference().trim());
        w.setSourceEffectiveDate(request.sourceEffectiveDate());
        w.setOvernightAllowed(request.overnightAllowed());
        w.setMaxStayMinutes(request.maxStayMinutes());
        w.setVerificationStatus("UNVERIFIED");
        w.setLifecycleStatus("INACTIVE");
        w.setVerifiedAt(null);
        w.setVerifiedBy(null);
        w.setVerifiedTimeZoneId(null);
        windows.saveAndFlush(w);
        audit.recordDirectoryMutation(
                "PortCallWindow",
                w.getWindowId(),
                id == null ? "CREATE" : "UPDATE",
                snap(before),
                snap(view(w)));
        return view(w);
    }

    @Transactional
    public Window verify(UUID portId, UUID id, Revision request) {
        Port p = lock(portId);
        var w = window(portId, id);
        revision(request.expectedRevision(), w.getRevision());
        var before = view(w);
        com.casms.ship.util.PortTimeZoneHelper.requirePortTimeZone(p.getTimeZoneId());
        w.setVerificationStatus("VERIFIED");
        w.setVerifiedBy(actor().getUserId());
        w.setVerifiedAt(Instant.now());
        w.setVerifiedTimeZoneId(p.getTimeZoneId());
        windows.saveAndFlush(w);
        audit.recordDirectoryMutation("PortCallWindow", id, "VERIFY", snap(before), snap(view(w)));
        return view(w);
    }

    @Transactional
    public Window lifecycle(UUID portId, UUID id, Lifecycle request) {
        Port p = lock(portId);
        var w = window(portId, id);
        revision(request.expectedRevision(), w.getRevision());
        var before = view(w);
        if ("ACTIVE".equals(request.lifecycleStatus())
                && (!"VERIFIED".equals(w.getVerificationStatus())
                        || !Objects.equals(p.getTimeZoneId(), w.getVerifiedTimeZoneId())))
            throw new BusinessException(
                    "WINDOW_NOT_VERIFIED",
                    "Xác minh lại khung giờ và múi giờ cảng trước khi kích hoạt.");
        w.setLifecycleStatus(request.lifecycleStatus());
        windows.saveAndFlush(w);
        audit.recordDirectoryMutation("PortCallWindow", id, "UPDATE", snap(before), snap(view(w)));
        return view(w);
    }

    @Transactional
    public Closure writeClosure(UUID portId, UUID id, ClosureWrite request) {
        Port p = lock(portId);
        var c = id == null ? new PortCallWindowException() : closure(portId, id);
        Object before = id == null ? null : view(c);
        if (id != null) revision(request.expectedRevision(), c.getRevision());
        dates(request.validFrom(), request.validTo(), request.sourceEffectiveDate());
        c.setPort(p);
        c.setValidFrom(request.validFrom());
        c.setValidTo(request.validTo());
        c.setReason(request.reason().trim());
        c.setSourceType(request.sourceType());
        c.setSourceReference(request.sourceReference().trim());
        c.setSourceEffectiveDate(request.sourceEffectiveDate());
        c.setVerificationStatus("UNVERIFIED");
        c.setLifecycleStatus("INACTIVE");
        c.setVerifiedBy(null);
        c.setVerifiedAt(null);
        c.setVerifiedTimeZoneId(null);
        closures.saveAndFlush(c);
        audit.recordDirectoryMutation(
                "PortCallWindowException",
                c.getExceptionId(),
                id == null ? "CREATE" : "UPDATE",
                snap(before),
                snap(view(c)));
        return view(c);
    }

    @Transactional
    public Closure verifyClosure(UUID portId, UUID id, Revision request) {
        Port p = lock(portId);
        var c = closure(portId, id);
        revision(request.expectedRevision(), c.getRevision());
        var before = view(c);
        com.casms.ship.util.PortTimeZoneHelper.requirePortTimeZone(p.getTimeZoneId());
        c.setVerificationStatus("VERIFIED");
        c.setVerifiedBy(actor().getUserId());
        c.setVerifiedAt(Instant.now());
        c.setVerifiedTimeZoneId(p.getTimeZoneId());
        closures.saveAndFlush(c);
        audit.recordDirectoryMutation(
                "PortCallWindowException", id, "VERIFY", snap(before), snap(view(c)));
        return view(c);
    }

    @Transactional
    public Closure lifecycleClosure(UUID portId, UUID id, Lifecycle request) {
        Port p = lock(portId);
        var c = closure(portId, id);
        revision(request.expectedRevision(), c.getRevision());
        var before = view(c);
        if ("ACTIVE".equals(request.lifecycleStatus())
                && (!"VERIFIED".equals(c.getVerificationStatus())
                        || !Objects.equals(p.getTimeZoneId(), c.getVerifiedTimeZoneId())))
            throw new BusinessException(
                    "WINDOW_NOT_VERIFIED",
                    "Xác minh lại ngày đóng cảng và múi giờ trước khi kích hoạt.");
        c.setLifecycleStatus(request.lifecycleStatus());
        closures.saveAndFlush(c);
        audit.recordDirectoryMutation(
                "PortCallWindowException", id, "UPDATE", snap(before), snap(view(c)));
        return view(c);
    }

    private Window view(PortCallWindow w) {
        return new Window(
                w.getWindowId(),
                w.getWindowType(),
                w.getValidFrom(),
                w.getValidTo(),
                Set.copyOf(w.getDaysOfWeek()),
                w.getLocalStartTime(),
                w.getLocalEndTime(),
                w.getSourceType(),
                w.getSourceReference(),
                w.getSourceEffectiveDate(),
                w.getOvernightAllowed(),
                w.getMaxStayMinutes(),
                w.getVerificationStatus(),
                w.getLifecycleStatus(),
                w.getVerifiedBy(),
                w.getVerifiedAt(),
                w.getVerifiedTimeZoneId(),
                w.getRevision(),
                w.getCreatedAt(),
                w.getUpdatedAt());
    }

    private Closure view(PortCallWindowException c) {
        return new Closure(
                c.getExceptionId(),
                c.getExceptionType(),
                c.getValidFrom(),
                c.getValidTo(),
                c.getReason(),
                c.getSourceType(),
                c.getSourceReference(),
                c.getSourceEffectiveDate(),
                c.getVerificationStatus(),
                c.getLifecycleStatus(),
                c.getVerifiedBy(),
                c.getVerifiedAt(),
                c.getVerifiedTimeZoneId(),
                c.getRevision(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
