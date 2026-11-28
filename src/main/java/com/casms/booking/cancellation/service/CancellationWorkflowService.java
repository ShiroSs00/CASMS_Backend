package com.casms.booking.cancellation.service;

import com.casms.booking.cancellation.entity.CancellationCase;
import com.casms.booking.cancellation.entity.CancellationCase.Scenario;
import com.casms.booking.cancellation.entity.CancellationCase.Status;
import com.casms.booking.cancellation.repository.CancellationCaseRepository;
import com.casms.booking.dto.request.BookingCancelRequest;
import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Role;
import com.casms.payment.dto.request.RefundCreateRequest;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.service.RefundService;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.OperationalIssue;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.BookingAgentCruiseScope;
import com.casms.ship.repository.OperationalIssueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancellationWorkflowService {
    private static final EnumSet<Status> ACTIVE = EnumSet.of(Status.PENDING_OPS, Status.PENDING_FINANCE);
    private final CancellationCaseRepository cases;
    private final BookingRepository bookings;
    private final CruiseRepository cruises;
    private final OperationalIssueRepository issues;
    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;

    public record CaseView(UUID caseId, UUID bookingId, Scenario scenario, Status status, String reason,
            Instant requestedAt, Instant departureAt, long revision, UUID issueId, UUID verifiedBy,
            Instant verifiedAt, UUID decidedBy, Instant decidedAt, String decisionReason,
            String financialReview, List<String> allowedActions) {}

    @Transactional
    public CaseView request(UUID bookingId, Scenario scenario, String reason, UUID issueId,
            String idempotencyKey, CasmsPrincipal actor) {
        requireActor(actor);
        var booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        boolean agent = actor.hasRole(Role.BOOKING_AGENT) && actor.hasAuthority("BOOKING_CANCEL");
        if (!isOwner(booking, actor) && !agent) throw denied();
        if (agent && !isOwner(booking, actor)) requireAssignment(booking, actor);
        if (scenario == null || (!agent && scenario != Scenario.CUSTOMER)
                || (scenario == Scenario.OPERATIONAL_DISRUPTION) != (issueId != null)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CANCELLATION_INVALID_SCENARIO",
                    "Kiểm tra lý do hủy và sự cố vận hành liên quan.");
        }
        String normalizedReason = requiredText(reason, 1000);
        String key = requiredText(idempotencyKey, 128);
        var prior = cases.findByBookingIdAndRequestedByAndIdempotencyKey(bookingId, actor.getUserId(), key);
        if (prior.isPresent()) {
            var existing = prior.get();
            if (existing.getScenario() != scenario || !existing.getReason().equals(normalizedReason)
                    || !Objects.equals(existing.getIssueId(), issueId)) {
                throw conflict("CANCELLATION_IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã được dùng với nội dung khác.");
            }
            return view(existing, actor);
        }
        if (booking.getBookingStatus() == BookingStatus.Cancelled || cases.existsByBookingIdAndStatusIn(bookingId, ACTIVE)) {
            throw conflict("CANCELLATION_ALREADY_ACTIVE", "Đặt chỗ đã hủy hoặc đã có yêu cầu đang xử lý.");
        }
        Instant departureAt = booking.getCruise() == null ? null : booking.getCruise().getDepartureDatetime();
        if (departureAt != null && !departureAt.isAfter(Instant.now())) {
            throw conflict("CANCELLATION_AFTER_DEPARTURE", "Chuyến khởi hành đã xuất bến nên không thể gửi yêu cầu hủy đặt chỗ.");
        }
        if (issueId != null) requireIssueOnBooking(booking, issueId);
        var cancellation = new CancellationCase();
        cancellation.setBookingId(bookingId);
        cancellation.setRequestedBy(actor.getUserId());
        cancellation.setScenario(scenario);
        cancellation.setReason(normalizedReason);
        cancellation.setIdempotencyKey(key);
        cancellation.setIssueId(issueId);
        cancellation.setRequestedAt(Instant.now());
        cancellation.setDepartureAt(booking.getCruise().getDepartureDatetime());
        cancellation.setStatus(issueId == null ? Status.PENDING_FINANCE : Status.PENDING_OPS);
        return view(cases.saveAndFlush(cancellation), actor);
    }

    @Transactional(readOnly = true)
    public List<CaseView> history(UUID bookingId, CasmsPrincipal actor) {
        requireActor(actor);
        var booking = booking(bookingId);
        if (!isFinance(actor) && !isOwner(booking, actor)) {
            if (!actor.hasRole(Role.BOOKING_AGENT) || !actor.hasAuthority("BOOKING_READ")) throw denied();
            requireAssignment(booking, actor);
        }
        return cases.findByBookingIdOrderByRequestedAtDescCaseIdDesc(bookingId)
                .stream().map(c -> view(c, actor)).toList();
    }

    @Transactional(readOnly = true)
    public Page<CaseView> queue(int page, int size, UUID issueId, CasmsPrincipal actor) {
        requireActor(actor);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt", "caseId"));
        if (issueId == null && isFinance(actor)) {
            return cases.findByStatus(Status.PENDING_FINANCE, pageable).map(c -> view(c, actor));
        }
        if (actor.hasRole(Role.OPERATIONS_MANAGER)) {
            return cases.findAssignedOperationalQueue(actor.getUserId(), ACTIVE, issueId, pageable)
                    .map(c -> view(c, actor));
        }
        throw denied();
    }

    @Transactional(readOnly = true)
    public CaseView detail(UUID id, CasmsPrincipal actor) {
        requireActor(actor);
        var cancellation = cases.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CancellationCase", id));
        var booking = booking(cancellation.getBookingId());
        if (!isFinance(actor) && !isOwner(booking, actor) && !isAssignedOps(cancellation, actor)) {
            if (!actor.hasAuthority("BOOKING_READ")) throw denied();
            requireAssignment(booking, actor);
        }
        return view(cancellation, actor);
    }

    @Transactional
    public CaseView transition(UUID id, String action, long revision, String reason, CasmsPrincipal actor) {
        requireActor(actor);
        var cancellation = cases.lockById(id).orElseThrow(() -> new ResourceNotFoundException("CancellationCase", id));
        var booking = booking(cancellation.getBookingId());
        UUID actorId = actor.getUserId();
        switch (action) {
            case "verify" -> {
                if (!isAssignedOps(cancellation, actor)) throw denied();
            }
            case "approve", "reject" -> {
                if (!isFinance(actor) || actorId.equals(cancellation.getRequestedBy())
                        || actorId.equals(cancellation.getVerifiedBy())) throw denied();
            }
            case "withdraw" -> {
                if (!actorId.equals(cancellation.getRequestedBy())) throw denied();
                if (!isOwner(booking, actor)) requireAssignment(booking, actor);
            }
            default -> throw new ResourceNotFoundException("Cancellation action", action);
        }
        if (cancellation.getRevision() != revision) {
            throw conflict("CANCELLATION_STALE_REVISION", "Yêu cầu đã được cập nhật. Vui lòng tải lại.");
        }
        switch (action) {
            case "verify" -> {
                if (!ACTIVE.contains(cancellation.getStatus())) throw invalidState();
                var issue = requireIssueOnBooking(booking, cancellation.getIssueId());
                if (issue.getVerifiedAt() == null || issue.getVerifiedBy() == null) {
                    throw conflict("CANCELLATION_ISSUE_NOT_VERIFIED", "Cần xác minh sự cố vận hành trước.");
                }
                cancellation.setVerifiedBy(actorId);
                cancellation.setVerifiedAt(Instant.now());
                cancellation.setStatus(Status.PENDING_FINANCE);
            }
            case "approve", "reject" -> {
                requireStatus(cancellation, Status.PENDING_FINANCE);
                String decision = requiredText(reason, 1000);
                if ("approve".equals(action)) {
                    if (cancellation.getIssueId() != null) {
                        var issue = requireIssueOnBooking(booking, cancellation.getIssueId());
                        if (cancellation.getVerifiedAt() == null || issue.getVerifiedAt() == null
                                || issue.getVerifiedAt().isAfter(cancellation.getVerifiedAt())) {
                            throw conflict("CANCELLATION_ISSUE_NOT_VERIFIED", "Cần xác minh lại sự cố vận hành.");
                        }
                    }
                    var request = new BookingCancelRequest();
                    request.setReason(cancellation.getReason());
                    bookingService.cancel(booking.getBookingId(), request, actor);
                    cancellation.setStatus(Status.COMPLETED);
                    createPendingRefundsForCancellation(booking.getBookingId(), cancellation.getCaseId(), decision);
                } else {
                    cancellation.setStatus(Status.REJECTED);
                }
                cancellation.setDecisionReason(decision);
                cancellation.setDecidedBy(actorId);
                cancellation.setDecidedAt(Instant.now());
            }
            case "withdraw" -> {
                if (!ACTIVE.contains(cancellation.getStatus())) throw invalidState();
                cancellation.setStatus(Status.WITHDRAWN);
                cancellation.setDecidedBy(actorId);
                cancellation.setDecidedAt(Instant.now());
            }
            default -> throw new IllegalStateException("Unexpected cancellation action");
        }
        return view(cases.saveAndFlush(cancellation), actor);
    }

    private OperationalIssue requireIssueOnBooking(Booking booking, UUID id) {
        return issues.findById(id)
                .filter(i -> i.getCruise() != null && i.getCruise().getCruiseId().equals(booking.getCruise().getCruiseId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "CANCELLATION_ISSUE_MISMATCH",
                        "Sự cố không thuộc chuyến của đặt chỗ."));
    }

    private boolean isAssignedOps(CancellationCase cancellation, CasmsPrincipal actor) {
        if (!actor.hasRole(Role.OPERATIONS_MANAGER) || cancellation.getIssueId() == null) return false;
        return issues.findById(cancellation.getIssueId()).filter(issue ->
                (issue.getAssignedTo() != null && actor.getUserId().equals(issue.getAssignedTo().getUserId()))
                || (issue.getVerifiedBy() != null && actor.getUserId().equals(issue.getVerifiedBy().getUserId()))).isPresent();
    }

    private static boolean isOwner(Booking booking, CasmsPrincipal actor) {
        return actor.hasRole(Role.PASSENGER) && actor.getUserId().equals(booking.getBookedByUser().getUserId());
    }

    private static boolean isFinance(CasmsPrincipal actor) {
        return actor.hasRole(Role.FINANCE_OFFICER) && actor.hasAuthority("PAYMENT_REFUND");
    }

    private void requireAssignment(Booking booking, CasmsPrincipal actor) {
        if (!actor.hasRole(Role.BOOKING_AGENT)) throw denied();
        BookingAgentCruiseScope.requireAssigned(cruises, booking.getCruise().getCruiseId(), actor.getUserId());
    }

    private Booking booking(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    private CaseView view(CancellationCase c, CasmsPrincipal actor) {
        boolean finance = isFinance(actor);
        boolean ops = isAssignedOps(c, actor);
        var actions = new java.util.ArrayList<String>();
        if (ops && ACTIVE.contains(c.getStatus())) actions.add("verify");
        if (finance && c.getStatus() == Status.PENDING_FINANCE
                && !actor.getUserId().equals(c.getRequestedBy()) && !actor.getUserId().equals(c.getVerifiedBy())) {
            actions.addAll(List.of("approve", "reject"));
        }
        if (ACTIVE.contains(c.getStatus()) && actor.getUserId().equals(c.getRequestedBy())) actions.add("withdraw");
        return new CaseView(c.getCaseId(), c.getBookingId(), c.getScenario(), c.getStatus(), c.getReason(),
                c.getRequestedAt(), c.getDepartureAt(), c.getRevision(), finance || ops ? c.getIssueId() : null,
                finance || ops ? c.getVerifiedBy() : null, finance || ops ? c.getVerifiedAt() : null,
                finance || ops ? c.getDecidedBy() : null, c.getDecidedAt(),
                (finance || c.getStatus() == Status.REJECTED || c.getStatus() == Status.COMPLETED)
                        ? c.getDecisionReason()
                        : null,
                "MANUAL_REVIEW_REQUIRED", List.copyOf(actions));
    }

    /**
     * RG-06 (CASMS-REFUND-001): Auto-create idempotent PENDING (Requested) refund records when
     * an approved cancellation has confirmed payments > 0. Never auto-disburses funds.
     */
    private void createPendingRefundsForCancellation(UUID bookingId, UUID caseId, String decision) {
        if (paymentRepository == null || refundService == null) {
            return;
        }
        List<Payment> confirmedPayments =
                paymentRepository.findForBookingReadOnly(bookingId).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.Confirmed)
                        .filter(
                                p ->
                                        p.getConfirmedAmount() != null
                                                && p.getConfirmedAmount().compareTo(BigDecimal.ZERO) > 0)
                        .toList();
        for (Payment payment : confirmedPayments) {
            RefundCreateRequest refundReq = new RefundCreateRequest();
            refundReq.setOriginalPaymentId(payment.getPaymentId());
            refundReq.setRequestedAmount(payment.getConfirmedAmount());
            refundReq.setCurrency(payment.getCurrency());
            refundReq.setRefundReason("Hoàn tiền theo duyệt hủy đặt chỗ: " + decision);
            refundReq.setIdempotencyKey("CANCEL_CASE:" + caseId + ":" + payment.getPaymentId());
            refundService.createRefund(refundReq);
        }
    }

    private static void requireActor(CasmsPrincipal actor) {
        if (actor == null || actor.getUserId() == null || actor.hasRole(Role.POS)) throw denied();
    }

    private static String requiredText(String text, int max) {
        if (text == null || text.isBlank() || text.strip().length() > max) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CANCELLATION_INVALID_TEXT",
                    "Thông tin bắt buộc bị thiếu hoặc vượt giới hạn.");
        }
        return text.strip();
    }

    private static void requireStatus(CancellationCase c, Status status) {
        if (c.getStatus() != status) throw invalidState();
    }

    private static BusinessException invalidState() {
        return conflict("CANCELLATION_INVALID_STATE", "Trạng thái yêu cầu không cho phép thao tác này.");
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Bạn không có quyền xử lý yêu cầu hủy này.");
    }
}
