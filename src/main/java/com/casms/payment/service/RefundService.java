package com.casms.payment.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.request.*;
import com.casms.payment.dto.response.RefundLineResponse;
import com.casms.payment.dto.response.RefundResponse;
import com.casms.payment.entity.*;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.RefundStatus;
import com.casms.payment.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final RefundLineRepository refundLineRepository;
    private final PaymentRepository paymentRepository;
    private final FinalSettlementRepository finalSettlementRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;

    @Transactional
    public RefundResponse createRefund(RefundCreateRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_IDEMPOTENCY_KEY_REQUIRED",
                    "Khóa chống trùng lặp là bắt buộc.");
        }
        String key = request.getIdempotencyKey().trim();
        if (request.getRefundReason() == null || request.getRefundReason().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_REASON_REQUIRED",
                    "Lý do hoàn tiền là bắt buộc.");
        }
        String reason = request.getRefundReason().trim();

        if (request.getRequestedAmount() == null
                || request.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUESTED_AMOUNT",
                    "Requested refund amount must be strictly greater than 0");
        }

        Payment payment =
                paymentRepository
                        .findByIdForUpdate(request.getOriginalPaymentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Payment", request.getOriginalPaymentId()));

        String paymentCurrency = payment.getCurrency();
        if (paymentCurrency == null || paymentCurrency.isBlank()
                || (request.getCurrency() != null && !paymentCurrency.strip().equalsIgnoreCase(request.getCurrency().strip()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_CURRENCY_MISMATCH",
                    "Kiểm tra đơn vị tiền tệ theo giao dịch thanh toán gốc.");
        }
        validateRefundMoneyPrecision(
                request.getRequestedAmount(), paymentCurrency, "INVALID_REQUESTED_AMOUNT");

        var existing = refundRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Refund refund = existing.get();
            if (!payment.getPaymentId().equals(refund.getOriginalPayment().getPaymentId())
                    || request.getRequestedAmount().compareTo(refund.getRequestedAmount()) != 0
                    || !Objects.equals(reason, refund.getRefundReason())
                    || !Objects.equals(request.getFinalSettlementId(), refund.getFinalSettlement() == null
                            ? null : refund.getFinalSettlement().getFinalSettlementId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "REFUND_IDEMPOTENCY_CONFLICT",
                        "Khóa chống trùng lặp đã được dùng cho yêu cầu hoàn tiền khác.");
            }
            var lines = refundLineRepository.findByRefund_RefundId(refund.getRefundId());
            if (!matchesRequestedLines(request.getLines(), lines)) {
                throw new BusinessException(HttpStatus.CONFLICT, "REFUND_IDEMPOTENCY_CONFLICT",
                        "Khóa chống trùng lặp đã được dùng cho yêu cầu hoàn tiền khác.");
            }
            return toRefundResponse(refund, lines);
        }

        if (payment.getStatus() != PaymentStatus.Confirmed) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_NOT_CONFIRMED",
                    "Can only refund confirmed payments");
        }

        BigDecimal remainingRefundable = RefundBalance.remaining(payment,
                refundRepository.findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(payment.getPaymentId()));
        if (request.getRequestedAmount().compareTo(remainingRefundable) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_EXCEEDS_PAYMENT",
                    "Requested refund amount of " + request.getRequestedAmount()
                            + " exceeds remaining refundable balance of " + remainingRefundable);
        }

        FinalSettlement settlement = null;
        if (request.getFinalSettlementId() != null) {
            settlement =
                    finalSettlementRepository
                            .findById(request.getFinalSettlementId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "FinalSettlement",
                                                    request.getFinalSettlementId()));
        }

        if (settlement != null && (payment.getOnboardAccount() == null || settlement.getOnboardAccount() == null
                || !Objects.equals(payment.getOnboardAccount().getOnboardAccountId(),
                        settlement.getOnboardAccount().getOnboardAccountId())
                || (payment.getFinalSettlement() != null && !settlement.getFinalSettlementId()
                        .equals(payment.getFinalSettlement().getFinalSettlementId())))) {
            throw new BusinessException("REFUND_SETTLEMENT_MISMATCH",
                    "Phiên quyết toán không thuộc giao dịch thanh toán.");
        }
        List<RefundLine> lines = validateRequestedLines(payment, settlement, request);

        Refund refund = new Refund();
        refund.setOriginalPayment(payment);
        refund.setFinalSettlement(settlement);
        refund.setRefundReason(reason);
        refund.setRequestedAmount(request.getRequestedAmount());
        refund.setApprovedAmount(null);
        refund.setCurrency(paymentCurrency.strip());
        refund.setRequestedAt(Instant.now());
        refund.setStatus(RefundStatus.Requested);
        refund.setIdempotencyKey(key);

        Refund savedRefund = refundRepository.save(refund);

        List<RefundLine> savedLines = new ArrayList<>();
        for (var line : lines) {
            line.setRefund(savedRefund);
            savedLines.add(refundLineRepository.save(line));
        }
        return toRefundResponse(savedRefund, savedLines);
    }

    private void validateRefundMoneyPrecision(
            BigDecimal amount, String currency, String errorCode) {
        if (amount == null || currency == null) return;
        if ("VND".equalsIgnoreCase(currency.strip()) && amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    errorCode,
                    "Số tiền hoàn bằng VND phải là số nguyên (không chứa phần thập phân).");
        }
    }

    private List<RefundLine> validateRequestedLines(Payment payment, FinalSettlement settlement,
            RefundCreateRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) return List.of();

        // All manual writers acquire the original Payment first and then source lines
        // in ascending ID order, even when the same invoice covers several Payments.
        var inputs = request.getLines().stream()
                .sorted(Comparator.comparing(RefundLineRequest::getInvoiceLineId)).toList();
        List<RefundLine> lines = new ArrayList<>();
        Set<UUID> lineIds = new HashSet<>();
        UUID invoiceId = null;
        BigDecimal total = BigDecimal.ZERO;
        for (var input : inputs) {
            UUID lineId = input.getInvoiceLineId();
            if (!lineIds.add(lineId)) {
                throw new BusinessException("REFUND_DUPLICATE_INVOICE_LINE", "Dòng hóa đơn bị trùng.");
            }
            var source = invoiceLineRepository.findByIdForUpdate(lineId)
                    .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));
            validateInvoiceContext(payment, settlement, source.getInvoice());
            if (invoiceId == null) invoiceId = source.getInvoice().getInvoiceId();
            if (!invoiceId.equals(source.getInvoice().getInvoiceId())) {
                throw new BusinessException("REFUND_INVOICE_MISMATCH", "Các dòng hoàn tiền phải thuộc cùng một hóa đơn.");
            }
            BigDecimal base = input.getBaseRefundAmount();
            BigDecimal tax = input.getTaxRefundAmount();
            if (base == null || tax == null || base.signum() < 0 || tax.signum() < 0
                    || base.add(tax).signum() <= 0) {
                throw new BusinessException("REFUND_INVALID_LINE_AMOUNT", "Số tiền hoàn trên dòng hóa đơn không hợp lệ.");
            }
            validateRefundMoneyPrecision(base, payment.getCurrency(), "REFUND_INVALID_LINE_AMOUNT");
            validateRefundMoneyPrecision(tax, payment.getCurrency(), "REFUND_INVALID_LINE_AMOUNT");
            BigDecimal reservedBase = BigDecimal.ZERO;
            BigDecimal reservedTax = BigDecimal.ZERO;
            for (var allocation : refundLineRepository.findAllocationsForLine(lineId)) {
                if (allocation.getRefund().getStatus() == RefundStatus.Rejected) continue;
                reservedBase = reservedBase.add(allocation.getBaseRefundAmount());
                reservedTax = reservedTax.add(allocation.getTaxRefundAmount());
            }
            if (base.add(reservedBase).compareTo(source.getBaseAmountSnapshot()) > 0
                    || tax.add(reservedTax).compareTo(source.getTaxAmountSnapshot()) > 0) {
                throw new BusinessException("REFUND_LINE_EXCEEDS_BALANCE",
                        "Số tiền hoàn vượt quá số dư có thể hoàn trên dòng hóa đơn.");
            }
            var line = new RefundLine();
            line.setInvoiceLine(source);
            line.setBaseRefundAmount(base);
            line.setTaxRefundAmount(tax);
            lines.add(line);
            total = total.add(base).add(tax);
        }
        if (total.compareTo(request.getRequestedAmount()) != 0) {
            throw new BusinessException("REFUND_LINE_TOTAL_MISMATCH", "Tổng tiền các dòng phải bằng số tiền yêu cầu hoàn.");
        }
        return lines;
    }

    private static boolean matchesRequestedLines(List<RefundLineRequest> requested, List<RefundLine> existing) {
        if (requested == null || requested.isEmpty()) return existing.isEmpty();
        if (requested.size() != existing.size()) return false;
        Map<UUID, RefundLine> byInvoiceLine = new HashMap<>();
        for (var line : existing) byInvoiceLine.put(line.getInvoiceLine().getInvoiceLineId(), line);
        for (var input : requested) {
            var line = byInvoiceLine.remove(input.getInvoiceLineId());
            if (line == null || input.getBaseRefundAmount().compareTo(line.getBaseRefundAmount()) != 0
                    || input.getTaxRefundAmount().compareTo(line.getTaxRefundAmount()) != 0) return false;
        }
        return byInvoiceLine.isEmpty();
    }

    @Transactional
    public RefundResponse approveRefund(
            UUID refundId, RefundApproveRequest request, UUID approvedByUserId) {
        Refund refund =
                refundRepository
                        .findByIdForUpdate(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        if (refund.getStatus() != RefundStatus.Requested) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REFUND_STATUS",
                    "Can only approve refunds in Requested status, current: " + refund.getStatus());
        }

        if (request.getApprovedAmount() == null
                || request.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_APPROVED_AMOUNT",
                    "Approved amount must be strictly greater than 0");
        }
        validateRefundMoneyPrecision(
                request.getApprovedAmount(), refund.getCurrency(), "INVALID_APPROVED_AMOUNT");

        if (request.getApprovedAmount().compareTo(refund.getRequestedAmount()) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "APPROVED_AMOUNT_EXCEEDED",
                    "Approved amount cannot exceed requested amount of "
                            + refund.getRequestedAmount());
        }

        User user = null;
        if (approvedByUserId != null) {
            user = userRepository.findById(approvedByUserId).orElse(null);
        }

        refund.setApprovedAmount(request.getApprovedAmount());
        refund.setApprovedByUser(user);
        refund.setApprovedAt(Instant.now());
        refund.setStatus(RefundStatus.Approved);

        Refund saved = refundRepository.save(refund);
        List<RefundLine> lines = refundLineRepository.findByRefund_RefundId(refundId);
        return toRefundResponse(saved, lines);
    }

    @Transactional
    public RefundResponse completeRefund(UUID refundId, RefundCompleteRequest request) {
        Refund refund =
                refundRepository
                        .findByIdForUpdate(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        String reference = request.getProcessorOrReceiptReference();
        if (reference == null || reference.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_REFERENCE_REQUIRED",
                    "Cần mã biên lai hoặc mã giao dịch hoàn tiền.");
        }
        reference = reference.trim();
        if (refund.getStatus() == RefundStatus.Completed) {
            if (!reference.equals(refund.getProcessorOrReceiptReference())) {
                throw new BusinessException(HttpStatus.CONFLICT, "REFUND_COMPLETION_CONFLICT",
                        "Yêu cầu hoàn tiền đã hoàn tất với mã giao dịch khác.");
            }
            return toRefundResponse(refund, refundLineRepository.findByRefund_RefundId(refundId));
        }
        if (refund.getStatus() != RefundStatus.Approved) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_STATUS",
                    "Can only complete refunds in Approved status, current: " + refund.getStatus());
        }

        List<RefundLine> lines = refundLineRepository.findByRefund_RefundId(refundId);
        BigDecimal amount = Objects.requireNonNullElse(refund.getApprovedAmount(), refund.getRequestedAmount());
        if (!lines.isEmpty()) {
            UUID invoiceId = lines.getFirst().getInvoiceLine().getInvoice().getInvoiceId();
            for (var line : lines) {
                var invoice = line.getInvoiceLine().getInvoice();
                validateInvoiceContext(refund.getOriginalPayment(), refund.getFinalSettlement(), invoice);
                if (!invoiceId.equals(invoice.getInvoiceId())) {
                    throw new BusinessException("REFUND_INVOICE_MISMATCH",
                            "Các dòng hoàn tiền phải thuộc cùng một hóa đơn.");
                }
            }
            var adjustment = invoiceService.generateAdjustmentInvoice(invoiceId, amount, refund.getRefundReason());
            refund.setAdjustmentInvoiceId(adjustment.getInvoiceId());
        } else {
            UUID bookingId = bookingId(refund.getOriginalPayment());
            if (bookingId != null) {
                invoiceRepository.findFirstByBooking_BookingIdOrderByVersionNoDesc(bookingId)
                        .ifPresent(invoice -> {
                            validateInvoiceContext(refund.getOriginalPayment(), refund.getFinalSettlement(), invoice);
                            var adjustment = invoiceService.generateAdjustmentInvoice(
                                    invoice.getInvoiceId(), amount, refund.getRefundReason());
                            refund.setAdjustmentInvoiceId(adjustment.getInvoiceId());
                        });
            }
        }

        refund.setProcessorOrReceiptReference(reference);
        refund.setCompletedAt(Instant.now());
        refund.setStatus(RefundStatus.Completed);
        return toRefundResponse(refundRepository.save(refund), lines);
    }

    private static UUID bookingId(Payment payment) {
        if (payment == null) return null;
        if (payment.getBooking() != null) return payment.getBooking().getBookingId();
        return payment.getInstallment() == null || payment.getInstallment().getBooking() == null
                ? null : payment.getInstallment().getBooking().getBookingId();
    }

    private static void validateInvoiceContext(Payment payment, FinalSettlement settlement, Invoice invoice) {
        UUID expectedBooking = bookingId(payment);
        var paymentAccount = payment.getOnboardAccount();
        boolean matches = expectedBooking != null
                ? invoice.getBooking() != null && expectedBooking.equals(invoice.getBooking().getBookingId())
                : paymentAccount != null && invoice.getOnboardAccount() != null
                        && paymentAccount.getOnboardAccountId().equals(invoice.getOnboardAccount().getOnboardAccountId());
        if (!matches || payment.getCurrency() == null || invoice.getCurrency() == null
                || !payment.getCurrency().strip().equalsIgnoreCase(invoice.getCurrency().strip())
                || (settlement != null && settlement.getFinalInvoice() != null
                    && !settlement.getFinalInvoice().getInvoiceId().equals(invoice.getInvoiceId()))) {
            throw new BusinessException("REFUND_INVOICE_MISMATCH",
                    "Hóa đơn không thuộc giao dịch thanh toán hoặc không cùng đơn vị tiền tệ.");
        }
    }

    @Transactional
    public RefundResponse rejectRefund(UUID refundId, String reason, UUID actorId) {
        if (actorId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "REFUND_ACTOR_REQUIRED",
                    "Cần danh tính người từ chối yêu cầu hoàn tiền.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_REJECTION_REASON_REQUIRED",
                    "Lý do từ chối hoàn tiền là bắt buộc.");
        }
        String normalizedReason = reason.strip();
        Refund refund =
                refundRepository
                        .findByIdForUpdate(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        if (refund.getStatus() == RefundStatus.Rejected
                && actorId.equals(refund.getRejectedByUserId())
                && normalizedReason.equals(refund.getRejectionReason())) {
            return toRefundResponse(refund, refundLineRepository.findByRefund_RefundId(refundId));
        }
        if (refund.getStatus() != RefundStatus.Requested
                && refund.getStatus() != RefundStatus.Approved) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REFUND_STATUS",
                    "Cannot reject refund in status " + refund.getStatus());
        }

        refund.setStatus(RefundStatus.Rejected);
        refund.setRejectionReason(normalizedReason);
        refund.setRejectedByUserId(actorId);
        refund.setRejectedAt(Instant.now());
        Refund saved = refundRepository.save(refund);
        List<RefundLine> lines = refundLineRepository.findByRefund_RefundId(refundId);
        return toRefundResponse(saved, lines);
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(UUID refundId) {
        Refund refund =
                refundRepository
                        .findById(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));
        List<RefundLine> lines = refundLineRepository.findByRefund_RefundId(refundId);
        return toRefundResponse(refund, lines);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsForBooking(UUID bookingId) {
        List<Refund> refunds = refundRepository.findForBooking(bookingId);
        return refunds.stream()
                .map(
                        r ->
                                toRefundResponse(
                                        r,
                                        refundLineRepository.findByRefund_RefundId(
                                                r.getRefundId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsForPayment(UUID paymentId) {
        List<Refund> refunds =
                refundRepository.findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(paymentId);
        return refunds.stream()
                .map(
                        r ->
                                toRefundResponse(
                                        r,
                                        refundLineRepository.findByRefund_RefundId(
                                                r.getRefundId())))
                .toList();
    }

    private RefundResponse toRefundResponse(Refund r, List<RefundLine> lines) {
        List<RefundLineResponse> lineResponses =
                lines.stream()
                        .map(
                                l ->
                                        RefundLineResponse.builder()
                                                .refundLineId(l.getRefundLineId())
                                                .refundId(r.getRefundId())
                                                .invoiceLineId(
                                                        l.getInvoiceLine() != null
                                                                ? l.getInvoiceLine()
                                                                        .getInvoiceLineId()
                                                                : null)
                                                .baseRefundAmount(l.getBaseRefundAmount())
                                                .taxRefundAmount(l.getTaxRefundAmount())
                                                .reversalChargeId(
                                                        l.getReversalCharge() != null
                                                                ? l.getReversalCharge()
                                                                        .getChargeId()
                                                                : null)
                                                .build())
                        .toList();

        return RefundResponse.builder()
                .refundId(r.getRefundId())
                .originalPaymentId(
                        r.getOriginalPayment() != null
                                ? r.getOriginalPayment().getPaymentId()
                                : null)
                .finalSettlementId(
                        r.getFinalSettlement() != null
                                ? r.getFinalSettlement().getFinalSettlementId()
                                : null)
                .refundReason(r.getRefundReason())
                .requestedAmount(r.getRequestedAmount())
                .approvedAmount(r.getApprovedAmount())
                .currency(r.getCurrency())
                .requestedAt(r.getRequestedAt())
                .approvedByUserId(
                        r.getApprovedByUser() != null ? r.getApprovedByUser().getUserId() : null)
                .approvedAt(r.getApprovedAt())
                .rejectionReason(r.getRejectionReason())
                .rejectedByUserId(r.getRejectedByUserId())
                .rejectedAt(r.getRejectedAt())
                .adjustmentInvoiceId(r.getAdjustmentInvoiceId())
                .completedAt(r.getCompletedAt())
                .processorOrReceiptReference(r.getProcessorOrReceiptReference())
                .status(r.getStatus())
                .idempotencyKey(r.getIdempotencyKey())
                .lines(lineResponses)
                .build();
    }
}
