package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayReconciliationResolveRequest;
import com.casms.payment.dto.response.SepayReconciliationListResponse;
import com.casms.payment.dto.response.SepayTransactionResponse;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.entity.SepayReconciliationAudit;
import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.SepayReconciliationAction;
import com.casms.payment.enums.SepayReconciliationStatus;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.payment.repository.SepayReconciliationAuditRepository;
import com.casms.payment.repository.SepayTransactionRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SepayReconciliationService {

    private final SepayTransactionRepository sepayTransactionRepository;
    private final SepayReconciliationAuditRepository auditRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final InstallmentRepository installmentRepository;
    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final SepayWebhookIngestionService webhookIngestionService;

    @Transactional(readOnly = true)
    public SepayReconciliationListResponse list(
            SepayTransactionStatus status,
            SepayReconciliationStatus reconciliationStatus,
            UUID bookingId,
            String search,
            int page,
            int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String cleanSearch = search != null && !search.isBlank() ? search.trim() : null;

        Specification<SepayTransaction> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    Join<SepayTransaction, Booking> bookingJoin =
                            root.join("booking", JoinType.LEFT);
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    if (reconciliationStatus != null) {
                        predicates.add(
                                cb.equal(root.get("reconciliationStatus"), reconciliationStatus));
                    }
                    if (bookingId != null) {
                        predicates.add(cb.equal(bookingJoin.get("bookingId"), bookingId));
                    }
                    if (cleanSearch != null) {
                        String pattern = "%" + cleanSearch.toLowerCase(Locale.ROOT) + "%";
                        List<Predicate> searchOrs = new ArrayList<>();
                        searchOrs.add(cb.like(cb.lower(cb.coalesce(root.get("content"), "")), pattern));
                        searchOrs.add(
                                cb.like(cb.lower(cb.coalesce(root.get("referenceCode"), "")), pattern));
                        searchOrs.add(cb.like(cb.lower(cb.coalesce(root.get("code"), "")), pattern));
                        searchOrs.add(
                                cb.like(
                                        cb.lower(cb.coalesce(bookingJoin.get("bookingReference"), "")),
                                        pattern));
                        try {
                            long numericId = Long.parseLong(cleanSearch);
                            searchOrs.add(cb.equal(root.get("sepayId"), numericId));
                        } catch (NumberFormatException ignored) {
                            // Non-numeric search term; text predicates cover all string columns.
                        }
                        predicates.add(cb.or(searchOrs.toArray(new Predicate[0])));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<SepayTransaction> resultPage =
                sepayTransactionRepository.findAll(
                        spec,
                        PageRequest.of(
                                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        long pendingReviewCount =
                sepayTransactionRepository.countByReconciliationStatus(
                        SepayReconciliationStatus.PENDING_REVIEW);
        long retryRequiredCount =
                sepayTransactionRepository.countByStatus(SepayTransactionStatus.RETRY_REQUIRED);

        return SepayReconciliationListResponse.builder()
                .content(resultPage.getContent().stream().map(tx -> toResponse(tx, false)).toList())
                .page(safePage)
                .size(safeSize)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .pendingReviewCount(pendingReviewCount)
                .retryRequiredCount(retryRequiredCount)
                .build();
    }

    @Transactional(readOnly = true)
    public SepayTransactionResponse getDetail(UUID transactionId) {
        SepayTransaction tx =
                sepayTransactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Sepay transaction not found: " + transactionId));
        return toResponse(tx, true);
    }

    public SepayTransactionResponse retryPosting(
            UUID transactionId, UUID actorUserId, String actorUsername) {
        SepayTransaction tx =
                sepayTransactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Sepay transaction not found: " + transactionId));
        if (tx.getStatus() != SepayTransactionStatus.RETRY_REQUIRED
                && tx.getStatus() != SepayTransactionStatus.RECEIVED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SEPAY_RETRY_NOT_ALLOWED",
                    "Chỉ có thể thử ghi sổ lại cho sự kiện đang ở trạng thái cần xử lý lại.");
        }
        try {
            SepayWebhookResponse result = webhookIngestionService.postTransaction(transactionId);
            log.info(
                    "Finance user {} retried SePay transaction {}: {}",
                    actorUsername,
                    transactionId,
                    result.getMessage());
        } catch (SepayWebhookIngestionService.PostingBusinessRejectionException rej) {
            webhookIngestionService.markReconciliationFromRejection(transactionId, rej);
        } catch (RuntimeException ex) {
            webhookIngestionService.markRetryRequired(transactionId, ex.getMessage());
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SEPAY_RETRY_FAILED",
                    "Thử ghi sổ lại chưa thành công: " + ex.getMessage());
        }
        return getDetail(transactionId);
    }

    @Transactional
    public SepayTransactionResponse resolve(
            UUID transactionId,
            SepayReconciliationResolveRequest request,
            UUID actorUserId,
            String actorUsername) {
        SepayTransaction tx =
                sepayTransactionRepository
                        .findByIdForUpdate(transactionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Sepay transaction not found: " + transactionId));

        if (tx.getReconciliationStatus() == SepayReconciliationStatus.RESOLVED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "RECONCILIATION_ALREADY_RESOLVED",
                    "Giao dịch ngoại lệ này đã được đối soát trước đó.");
        }
        if (tx.getStatus() == SepayTransactionStatus.PROCESSED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "TRANSACTION_ALREADY_PROCESSED",
                    "Giao dịch này đã được ghi sổ thành công trước đó.");
        }
        if (tx.getStatus() == SepayTransactionStatus.IGNORED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "RECONCILIATION_NOT_APPLICABLE",
                    "Giao dịch bị bỏ qua không thuộc diện đối soát tiền vào.");
        }

        String reason = request.getReason().trim();
        String evidenceRef = request.getEvidenceReference().trim();
        SepayTransactionStatus previousStatus = tx.getStatus();
        Instant now = Instant.now();

        UUID linkedBookingId =
                request.getTargetBookingId() != null
                        ? request.getTargetBookingId()
                        : (tx.getBooking() != null ? tx.getBooking().getBookingId() : null);
        UUID linkedPaymentId = tx.getPayment() != null ? tx.getPayment().getPaymentId() : null;

        if (request.getAction() == SepayReconciliationAction.APPLY_TO_BOOKING) {
            if (linkedBookingId == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "TARGET_BOOKING_REQUIRED",
                        "Cần chọn đặt chỗ liên quan khi ghi nhận khoản thu từ giao dịch đối soát.");
            }
            Booking booking =
                    bookingRepository
                            .findByIdForUpdate(linkedBookingId)
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Booking", linkedBookingId));

            if (booking.getBookingStatus() == BookingStatus.Cancelled) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "BOOKING_CANCELLED",
                        "Không thể ghi nhận thu tiền vào đặt chỗ đã hủy. Vui lòng chọn Chuyển quy"
                                + " trình hoàn tiền.");
            }
            if (BookingPaymentBalanceHelper.isHoldExpired(booking, now)) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "BOOKING_HOLD_EXPIRED",
                        "Đặt chỗ đã hết thời hạn giữ chỗ. Không tự hồi sinh đặt chỗ; vui lòng chọn"
                                + " Chuyển quy trình hoàn tiền.");
            }
            String txCurrency = tx.getCurrency() != null ? tx.getCurrency().trim() : "VND";
            if (booking.getCurrency() == null
                    || !booking.getCurrency().trim().equalsIgnoreCase(txCurrency)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PAYMENT_CURRENCY_MISMATCH",
                        "Tiền tệ giao dịch ngân hàng ("
                                + txCurrency
                                + ") không khớp tiền tệ của đặt chỗ ("
                                + booking.getCurrency()
                                + ").");
            }
            BigDecimal amount = tx.getTransferAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_AMOUNT",
                        "Số tiền thực nhận không hợp lệ để ghi nhận.");
            }

            List<Payment> existingPayments = paymentRepository.findForBooking(booking.getBookingId());
            List<Refund> existingRefunds = refundRepository.findForBooking(booking.getBookingId());
            BigDecimal netPaid =
                    BookingPaymentBalanceHelper.verifiedNetPaid(
                            booking, existingPayments, existingRefunds);
            BigDecimal outstanding =
                    BookingPaymentBalanceHelper.outstandingBalance(booking, netPaid);
            if (amount.compareTo(outstanding) > 0) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "AMOUNT_EXCEEDS_OUTSTANDING_BALANCE",
                        "Số tiền thực nhận ("
                                + amount.stripTrailingZeros().toPlainString()
                                + " "
                                + txCurrency
                                + ") vượt quá số dư còn lại của đặt chỗ ("
                                + outstanding.stripTrailingZeros().toPlainString()
                                + " "
                                + txCurrency
                                + "). Vui lòng xử lý phần dư theo quy trình hoàn tiền.");
            }

            String processorRef = "SEPAY-" + tx.getSepayId();
            if (paymentRepository.findByProcessorReference(processorRef).isPresent()) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_PAYMENT_REFERENCE",
                        "Giao dịch SePay này đã từng được ghi sổ trước đó.");
            }

            List<Installment> installments =
                    installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                            booking.getBookingId());
            Map<UUID, BigDecimal> paidMap =
                    BookingPaymentBalanceHelper.allocateWaterfall(netPaid, installments);
            Installment targetInstallment =
                    installments.stream()
                            .filter(
                                    inst ->
                                            paidMap.getOrDefault(
                                                                    inst.getInstallmentId(),
                                                                    BigDecimal.ZERO)
                                                            .compareTo(inst.getRequiredAmount())
                                                    < 0)
                            .findFirst()
                            .orElse(installments.isEmpty() ? null : installments.get(0));

            Payment reconciledPayment = new Payment();
            reconciledPayment.setBooking(booking);
            reconciledPayment.setInstallment(targetInstallment);
            reconciledPayment.setOperationType(PaymentOperationType.Collect);
            reconciledPayment.setPaymentMethod(PaymentMethod.Sepay);
            reconciledPayment.setProvider("SEPAY_VIETQR_RECONCILED");
            reconciledPayment.setRequestedAmount(amount);
            reconciledPayment.setCurrency(booking.getCurrency());
            reconciledPayment.setOriginalAmount(amount);
            reconciledPayment.setOriginalCurrency(txCurrency);
            reconciledPayment.setConvertedAmount(amount);
            reconciledPayment.setSettlementCurrency(txCurrency);
            reconciledPayment.setRequestedAt(now);
            reconciledPayment.setStatus(PaymentStatus.Pending);
            reconciledPayment.setIdempotencyKey("SEPAY-RECON-" + tx.getSepayId());
            reconciledPayment.setInternalReceiptReference(evidenceRef);
            reconciledPayment = paymentRepository.save(reconciledPayment);

            paymentService.confirmPayment(
                    reconciledPayment.getPaymentId(),
                    new PaymentConfirmRequest(amount, processorRef));

            tx.setBooking(booking);
            tx.setPayment(reconciledPayment);
            tx.setStatus(SepayTransactionStatus.PROCESSED);
            tx.setMatchedAt(now);
            tx.setPostedAt(now);
            linkedPaymentId = reconciledPayment.getPaymentId();
        } else if (request.getTargetBookingId() != null && tx.getBooking() == null) {
            bookingRepository.findById(request.getTargetBookingId()).ifPresent(tx::setBooking);
        }

        tx.setReconciliationStatus(SepayReconciliationStatus.RESOLVED);
        tx.setReconciliationAction(request.getAction());
        tx.setReconciliationReason(reason);
        tx.setReconciliationEvidenceRef(evidenceRef);
        tx.setReconciledByUserId(actorUserId);
        tx.setReconciledByUsername(actorUsername);
        tx.setReconciledAt(now);
        sepayTransactionRepository.save(tx);

        webhookIngestionService.recordAudit(
                tx,
                request.getAction().name(),
                previousStatus != null ? previousStatus.name() : null,
                tx.getStatus().name(),
                actorUserId,
                actorUsername,
                reason,
                evidenceRef,
                tx.getBooking() != null ? tx.getBooking().getBookingId() : linkedBookingId,
                linkedPaymentId);

        log.info(
                "Finance user {} resolved SePay exception tx={} action={} evidence={}",
                actorUsername,
                transactionId,
                request.getAction(),
                evidenceRef);

        return toResponse(tx, true);
    }

    private SepayTransactionResponse toResponse(SepayTransaction tx, boolean includeAudit) {
        BigDecimal variance = null;
        if (tx.getExpectedAmount() != null && tx.getTransferAmount() != null) {
            variance = tx.getTransferAmount().subtract(tx.getExpectedAmount());
        }
        List<SepayTransactionResponse.AuditEntry> auditEntries = null;
        if (includeAudit && tx.getId() != null) {
            auditEntries =
                    auditRepository.findBySepayTransactionIdOrderByCreatedAtAsc(tx.getId()).stream()
                            .map(this::toAuditEntry)
                            .toList();
        }

        return SepayTransactionResponse.builder()
                .id(tx.getId())
                .sepayId(tx.getSepayId())
                .gateway(tx.getGateway())
                .transactionDate(tx.getTransactionDate())
                .accountNumber(tx.getAccountNumber())
                .transferType(tx.getTransferType())
                .transferAmount(tx.getTransferAmount())
                .currency(tx.getCurrency() != null ? tx.getCurrency() : "VND")
                .expectedAmount(tx.getExpectedAmount())
                .varianceAmount(variance)
                .code(tx.getCode())
                .content(tx.getContent())
                .referenceCode(tx.getReferenceCode())
                .description(tx.getDescription())
                .bookingId(tx.getBooking() != null ? tx.getBooking().getBookingId() : null)
                .bookingReference(
                        tx.getBooking() != null ? tx.getBooking().getBookingReference() : null)
                .bookingStatus(tx.getBooking() != null ? tx.getBooking().getBookingStatus() : null)
                .paymentId(tx.getPayment() != null ? tx.getPayment().getPaymentId() : null)
                .paymentReceiptReference(
                        tx.getPayment() != null
                                ? tx.getPayment().getInternalReceiptReference()
                                : null)
                .status(tx.getStatus())
                .exceptionCode(tx.getExceptionCode())
                .exceptionReason(tx.getExceptionReason())
                .reconciliationStatus(tx.getReconciliationStatus())
                .reconciliationAction(tx.getReconciliationAction())
                .reconciliationReason(tx.getReconciliationReason())
                .reconciliationEvidenceRef(tx.getReconciliationEvidenceRef())
                .reconciledByUserId(tx.getReconciledByUserId())
                .reconciledByUsername(tx.getReconciledByUsername())
                .reconciledAt(tx.getReconciledAt())
                .matchedAt(tx.getMatchedAt())
                .postedAt(tx.getPostedAt())
                .retryCount(tx.getRetryCount())
                .lastError(tx.getLastError())
                .lastAttemptAt(tx.getLastAttemptAt())
                .createdAt(tx.getCreatedAt())
                .auditHistory(auditEntries)
                .build();
    }

    private SepayTransactionResponse.AuditEntry toAuditEntry(SepayReconciliationAudit entry) {
        return SepayTransactionResponse.AuditEntry.builder()
                .id(entry.getId())
                .action(entry.getAction())
                .fromStatus(entry.getFromStatus())
                .toStatus(entry.getToStatus())
                .actorUserId(entry.getActorUserId())
                .actorUsername(entry.getActorUsername())
                .reason(entry.getReason())
                .evidenceReference(entry.getEvidenceReference())
                .relatedBookingId(entry.getRelatedBookingId())
                .relatedPaymentId(entry.getRelatedPaymentId())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
