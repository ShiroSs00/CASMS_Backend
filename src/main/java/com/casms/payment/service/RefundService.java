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
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;

    @Transactional
    public RefundResponse createRefund(RefundCreateRequest request) {
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Refund> existing =
                    refundRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Idempotent hit for refund request {}", request.getIdempotencyKey());
                Refund ref = existing.get();
                return toRefundResponse(
                        ref, refundLineRepository.findByRefund_RefundId(ref.getRefundId()));
            }
        }

        Payment payment =
                paymentRepository
                        .findById(request.getOriginalPaymentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Payment", request.getOriginalPaymentId()));

        if (payment.getStatus() != PaymentStatus.Confirmed) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_NOT_CONFIRMED",
                    "Can only refund confirmed payments");
        }

        BigDecimal maxRefundable =
                payment.getConfirmedAmount() != null
                        ? payment.getConfirmedAmount()
                        : payment.getRequestedAmount();
        if (request.getRequestedAmount().compareTo(maxRefundable) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "REFUND_EXCEEDS_PAYMENT",
                    "Requested refund amount exceeds confirmed payment amount of " + maxRefundable);
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

        Refund refund = new Refund();
        refund.setOriginalPayment(payment);
        refund.setFinalSettlement(settlement);
        refund.setRefundReason(request.getRefundReason().trim());
        refund.setRequestedAmount(request.getRequestedAmount());
        refund.setApprovedAmount(null);
        refund.setCurrency(payment.getCurrency() != null ? payment.getCurrency() : "USD");
        refund.setRequestedAt(Instant.now());
        refund.setStatus(RefundStatus.Requested);
        refund.setIdempotencyKey(request.getIdempotencyKey().trim());

        Refund savedRefund = refundRepository.save(refund);

        List<RefundLine> savedLines = new ArrayList<>();
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            for (RefundLineRequest lineReq : request.getLines()) {
                InvoiceLine invLine =
                        invoiceLineRepository
                                .findById(lineReq.getInvoiceLineId())
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "InvoiceLine", lineReq.getInvoiceLineId()));

                RefundLine line = new RefundLine();
                line.setRefund(savedRefund);
                line.setInvoiceLine(invLine);
                line.setBaseRefundAmount(lineReq.getBaseRefundAmount());
                line.setTaxRefundAmount(lineReq.getTaxRefundAmount());
                line.setReversalCharge(null);
                savedLines.add(refundLineRepository.save(line));
            }
        }

        return toRefundResponse(savedRefund, savedLines);
    }

    @Transactional
    public RefundResponse approveRefund(
            UUID refundId, RefundApproveRequest request, UUID approvedByUserId) {
        Refund refund =
                refundRepository
                        .findById(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        if (refund.getStatus() != RefundStatus.Requested) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REFUND_STATUS",
                    "Can only approve refunds in Requested status, current: " + refund.getStatus());
        }

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
                        .findById(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        if (refund.getStatus() != RefundStatus.Approved) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REFUND_STATUS",
                    "Can only complete refunds in Approved status, current: " + refund.getStatus());
        }

        refund.setProcessorOrReceiptReference(request.getProcessorOrReceiptReference().trim());
        refund.setCompletedAt(Instant.now());
        refund.setStatus(RefundStatus.Completed);

        Refund saved = refundRepository.save(refund);

        // If there are attached refund lines, generate adjustment statement
        List<RefundLine> lines = refundLineRepository.findByRefund_RefundId(refundId);
        if (!lines.isEmpty()) {
            UUID invoiceId = lines.get(0).getInvoiceLine().getInvoice().getInvoiceId();
            BigDecimal refundAmt =
                    saved.getApprovedAmount() != null
                            ? saved.getApprovedAmount()
                            : saved.getRequestedAmount();
            try {
                invoiceService.generateAdjustmentInvoice(
                        invoiceId, refundAmt, saved.getRefundReason());
            } catch (Exception ex) {
                log.warn("Adjustment invoice generation skipped or failed: {}", ex.getMessage());
            }
        }

        return toRefundResponse(saved, lines);
    }

    @Transactional
    public RefundResponse rejectRefund(UUID refundId, String reason) {
        Refund refund =
                refundRepository
                        .findById(refundId)
                        .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        if (refund.getStatus() != RefundStatus.Requested
                && refund.getStatus() != RefundStatus.Approved) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REFUND_STATUS",
                    "Cannot reject refund in status " + refund.getStatus());
        }

        refund.setStatus(RefundStatus.Rejected);
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
                .completedAt(r.getCompletedAt())
                .processorOrReceiptReference(r.getProcessorOrReceiptReference())
                .status(r.getStatus())
                .idempotencyKey(r.getIdempotencyKey())
                .lines(lineResponses)
                .build();
    }
}
