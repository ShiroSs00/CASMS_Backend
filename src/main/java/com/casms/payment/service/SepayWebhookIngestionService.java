package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.Refund;
import com.casms.payment.entity.SepayReconciliationAudit;
import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.SepayReconciliationStatus;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.payment.repository.SepayReconciliationAuditRepository;
import com.casms.payment.repository.SepayTransactionRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-phase transactional processor for SePay webhooks:
 * 1) Persists incoming SePay events once (unique sepay_id) in an independent transaction.
 * 2) Locks & posts exact-match VietQR booking payments or routes mismatches/exceptions to Finance
 *    reconciliation without resurrecting cancelled/expired bookings or double-counting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookIngestionService {

    private static final DateTimeFormatter SEPAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BOOKING_REF_PATTERN =
            Pattern.compile(
                    "BK[-_]?[A-Za-z0-9]{3,16}(?:[-_][A-Za-z0-9]{1,8})?",
                    Pattern.CASE_INSENSITIVE);

    private final SepayProperties sepayProperties;
    private final SepayTransactionRepository sepayTransactionRepository;
    private final SepayReconciliationAuditRepository auditRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final RefundRepository refundRepository;
    private final PaymentService paymentService;

    @PersistenceContext private EntityManager entityManager;

    public record IngestionResult(UUID transactionId, boolean newlyCreated, SepayTransactionStatus currentStatus) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionResult ingestEvent(SepayWebhookPayload payload) {
        Long sepayId = payload.getId() != null ? payload.getId() : System.currentTimeMillis();

        Optional<SepayTransaction> existingOpt = sepayTransactionRepository.findBySepayId(sepayId);
        if (existingOpt.isPresent()) {
            SepayTransaction existing = existingOpt.get();
            return new IngestionResult(existing.getId(), false, existing.getStatus());
        }

        SepayTransaction tx = new SepayTransaction();
        tx.setSepayId(sepayId);
        String gateway = payload.getGateway();
        if ((gateway == null || gateway.isBlank()) && payload.getCardBrand() != null) {
            gateway = "SEPAY_" + payload.getCardBrand().trim().toUpperCase(Locale.ROOT);
        } else if ((gateway == null || gateway.isBlank()) && payload.getPaymentChannel() != null) {
            gateway = "SEPAY_" + payload.getPaymentChannel().trim().toUpperCase(Locale.ROOT);
        }
        tx.setGateway(gateway);
        tx.setAccountNumber(
                payload.getAccountNumber() != null
                        ? payload.getAccountNumber().trim()
                        : payload.getMaskedCardNumber());
        tx.setTransferType(
                payload.getTransferType() != null ? payload.getTransferType().trim() : null);
        tx.setTransferAmount(
                payload.getTransferAmount() != null ? payload.getTransferAmount() : BigDecimal.ZERO);
        tx.setCurrency(normalizeCurrency(payload.getCurrency()));
        tx.setAccumulated(payload.getAccumulated());
        tx.setCode(payload.getCode());
        tx.setContent(payload.getContent());
        tx.setReferenceCode(payload.getReferenceCode());
        tx.setDescription(payload.getDescription());
        tx.setStatus(SepayTransactionStatus.RECEIVED);
        tx.setReconciliationStatus(SepayReconciliationStatus.NONE);
        tx.setRetryCount(0);
        tx.setCreatedAt(Instant.now());
        tx.setTransactionDate(parseTransactionDate(payload.getTransactionDate()));

        try {
            SepayTransaction saved = sepayTransactionRepository.saveAndFlush(tx);
            recordAudit(
                    saved,
                    "WEBHOOK_RECEIVED",
                    null,
                    SepayTransactionStatus.RECEIVED.name(),
                    null,
                    "SYSTEM_SEPAY_WEBHOOK",
                    "Tiếp nhận sự kiện biến động số dư từ SePay",
                    saved.getReferenceCode(),
                    null,
                    null);
            return new IngestionResult(saved.getId(), true, saved.getStatus());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Concurrent duplicate SePay webhook detected for sepayId={}", sepayId);
            SepayTransaction existing =
                    sepayTransactionRepository
                            .findBySepayId(sepayId)
                            .orElseThrow(() -> duplicate);
            return new IngestionResult(existing.getId(), false, existing.getStatus());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SepayWebhookResponse postTransaction(UUID transactionId) {
        if (entityManager != null) {
            // Clear any stale entity cached in an outer OSIV EntityManager before acquiring the row lock.
            entityManager.clear();
        }
        SepayTransaction tx =
                sepayTransactionRepository
                        .findByIdForUpdate(transactionId)
                        .or(() -> sepayTransactionRepository.findById(transactionId))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "SepayTransaction not found: " + transactionId));

        SepayTransactionStatus previousStatus = tx.getStatus();
        if (previousStatus == SepayTransactionStatus.PROCESSED
                || previousStatus == SepayTransactionStatus.IGNORED
                || tx.getReconciliationStatus() == SepayReconciliationStatus.RESOLVED
                || (previousStatus != SepayTransactionStatus.RECEIVED
                        && previousStatus != SepayTransactionStatus.RETRY_REQUIRED)) {
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Transaction already processed")
                    .paymentId(tx.getPayment() != null ? tx.getPayment().getPaymentId() : null)
                    .bookingReference(
                            tx.getBooking() != null ? tx.getBooking().getBookingReference() : null)
                    .build();
        }

        tx.setLastAttemptAt(Instant.now());

        // 1. Validate transfer direction
        if (tx.getTransferType() == null || tx.getTransferType().isBlank()) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "INVALID_TRANSFER_TYPE",
                    "Thiếu loại biến động giao dịch (transferType) từ SePay.",
                    null);
        }
        if (!"in".equalsIgnoreCase(tx.getTransferType().trim())) {
            tx.setStatus(SepayTransactionStatus.IGNORED);
            tx.setReconciliationStatus(SepayReconciliationStatus.NONE);
            sepayTransactionRepository.save(tx);
            recordAudit(
                    tx,
                    "IGNORED_NON_INCOMING",
                    previousStatus.name(),
                    SepayTransactionStatus.IGNORED.name(),
                    null,
                    "SYSTEM_SEPAY_WEBHOOK",
                    "Bỏ qua giao dịch không phải tiền vào (" + tx.getTransferType() + ")",
                    tx.getReferenceCode(),
                    null,
                    null);
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Ignored non-incoming transfer")
                    .build();
        }

        // 2. Validate positive amount
        if (tx.getTransferAmount() == null || tx.getTransferAmount().signum() <= 0) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "INVALID_TRANSFER_AMOUNT",
                    "Số tiền chuyển khoản không hợp lệ (<= 0).",
                    null);
        }

        // 3. Validate destination account for bank/VietQR transfers
        boolean isCardGateway =
                tx.getGateway() != null
                        && (tx.getGateway().toUpperCase(Locale.ROOT).contains("VISA")
                                || tx.getGateway().toUpperCase(Locale.ROOT).contains("MASTERCARD"));
        if (!isCardGateway
                && sepayProperties.getAccountNumber() != null
                && !sepayProperties.getAccountNumber().isBlank()
                && tx.getAccountNumber() != null
                && !tx.getAccountNumber().isBlank()) {
            String expectedAcc = normalizeDigits(sepayProperties.getAccountNumber());
            String actualAcc = normalizeDigits(tx.getAccountNumber());
            if (!expectedAcc.isEmpty() && !expectedAcc.equals(actualAcc)) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "ACCOUNT_MISMATCH",
                        "Tài khoản nhận ("
                                + tx.getAccountNumber()
                                + ") không khớp tài khoản SePay cấu hình ("
                                + sepayProperties.getAccountNumber()
                                + ").",
                        null);
            }
        }

        // 4. Match transfer memo against SePay orders
        String fullMemo = buildFullMemo(tx);
        Payment matchedPayment = findMatchingPayment(fullMemo);
        Booking matchedBooking = matchedPayment != null ? matchedPayment.getBooking() : null;
        if (matchedBooking == null) {
            matchedBooking = findMatchingBooking(fullMemo);
        }

        if (matchedBooking != null) {
            tx.setBooking(matchedBooking);
        }
        if (matchedPayment != null) {
            tx.setPayment(matchedPayment);
        }

        // 4a. Unmatched memo (neither order nor booking found)
        if (matchedPayment == null && matchedBooking == null) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.UNMATCHED,
                    "TRANSFER_CODE_UNMATCHED",
                    "Không tìm thấy lệnh thu hoặc mã đặt chỗ phù hợp trong nội dung chuyển khoản.",
                    null);
        }

        // 4b. Booking found, but no matching VietQR order (exact-match policy: do NOT auto-create)
        if (matchedPayment == null) {
            if (matchedBooking.getBookingStatus() == BookingStatus.Cancelled) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BOOKING_CANCELLED",
                        "Đặt chỗ "
                                + matchedBooking.getBookingReference()
                                + " đã bị hủy. Không tự hồi sinh đặt chỗ; chuyển Finance đối soát"
                                + " và xử lý hoàn tiền.",
                        null);
            }
            if (BookingPaymentBalanceHelper.isHoldExpired(matchedBooking, Instant.now())) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BOOKING_HOLD_EXPIRED",
                        "Đặt chỗ "
                                + matchedBooking.getBookingReference()
                                + " đã quá hạn giữ chỗ. Không tự hồi sinh đặt chỗ; chuyển Finance"
                                + " đối soát.",
                        null);
            }
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "NO_ACTIVE_ORDER",
                    "Chuyển khoản có nhắc mã đặt chỗ "
                            + matchedBooking.getBookingReference()
                            + " nhưng không khớp lệnh VietQR đang chờ hợp lệ. Chuyển Finance đối"
                            + " soát.",
                    null);
        }

        // 5. Lock payment & booking to evaluate authoritative state safely
        Payment lockedPayment =
                paymentRepository
                        .findByIdForUpdate(matchedPayment.getPaymentId())
                        .orElse(matchedPayment);
        Booking lockedBooking =
                lockedPayment.getBooking() != null
                        ? bookingRepository
                                .findByIdForUpdate(lockedPayment.getBooking().getBookingId())
                                .orElse(lockedPayment.getBooking())
                        : matchedBooking;
        tx.setPayment(lockedPayment);
        tx.setBooking(lockedBooking);

        BigDecimal expectedAmount = resolveExpectedWebhookAmount(lockedPayment, lockedBooking, tx.getCurrency());
        tx.setExpectedAmount(expectedAmount);

        // 5a. Order is no longer Pending (already settled / superseded / cancelled / failed)
        if (lockedPayment.getStatus() == PaymentStatus.Confirmed) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "ALREADY_SETTLED",
                    "Lệnh thanh toán "
                            + lockedPayment.getInternalReceiptReference()
                            + " đã được tất toán trước đó. Khoản tiền trùng được chuyển Finance đối"
                            + " soát.",
                    expectedAmount);
        }
        if (lockedPayment.getStatus() != PaymentStatus.Pending) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "ORDER_SUPERSEDED",
                    "Lệnh thanh toán "
                            + lockedPayment.getInternalReceiptReference()
                            + " không còn hiệu lực (trạng thái "
                            + lockedPayment.getStatus()
                            + "). Khoản tiền đến sau được chuyển Finance đối soát.",
                    expectedAmount);
        }

        // 5b. Validate booking eligibility (never resurrect cancelled or expired-hold bookings)
        if (lockedBooking != null) {
            if (lockedBooking.getBookingStatus() == BookingStatus.Cancelled) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BOOKING_CANCELLED",
                        "Đặt chỗ "
                                + lockedBooking.getBookingReference()
                                + " đã bị hủy. Không tự hồi sinh đặt chỗ; chuyển Finance đối soát"
                                + " và xử lý hoàn tiền.",
                        expectedAmount);
            }
            if (BookingPaymentBalanceHelper.isHoldExpired(lockedBooking, Instant.now())) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BOOKING_HOLD_EXPIRED",
                        "Đặt chỗ "
                                + lockedBooking.getBookingReference()
                                + " đã hết thời hạn giữ chỗ. Không tự xác nhận; chuyển Finance đối"
                                + " soát.",
                        expectedAmount);
            }

            List<Payment> bookingPayments =
                    paymentRepository.findForBooking(lockedBooking.getBookingId());
            List<Refund> bookingRefunds =
                    refundRepository != null
                            ? refundRepository.findForBooking(lockedBooking.getBookingId())
                            : List.of();
            BigDecimal netPaid =
                    BookingPaymentBalanceHelper.verifiedNetPaid(
                            lockedBooking, bookingPayments, bookingRefunds);
            BigDecimal outstanding =
                    BookingPaymentBalanceHelper.outstandingBalance(lockedBooking, netPaid);

            if (outstanding.signum() <= 0) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BOOKING_ALREADY_PAID",
                        "Đặt chỗ "
                                + lockedBooking.getBookingReference()
                                + " đã thanh toán đủ. Khoản tiền đến sau được chuyển Finance đối"
                                + " soát.",
                        expectedAmount);
            }
            if (lockedPayment.getRequestedAmount() != null
                    && lockedPayment.getRequestedAmount().compareTo(outstanding) > 0) {
                return markException(
                        tx,
                        previousStatus,
                        SepayTransactionStatus.RECONCILIATION_REQUIRED,
                        "BALANCE_CHANGED",
                        "Số dư còn lại của đặt chỗ đã nhỏ hơn số tiền của lệnh QR. Chuyển Finance"
                                + " đối soát để tránh thu dư.",
                        expectedAmount);
            }
        }

        // 5c. Validate currency match for VND VietQR booking flow
        boolean isBookingVnd =
                lockedBooking != null && "VND".equalsIgnoreCase(lockedBooking.getCurrency());
        if (isBookingVnd && !"VND".equalsIgnoreCase(tx.getCurrency())) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.RECONCILIATION_REQUIRED,
                    "CURRENCY_MISMATCH",
                    "Tiền tệ giao dịch ("
                            + tx.getCurrency()
                            + ") không khớp tiền tệ VND của đặt chỗ.",
                    expectedAmount);
        }

        // 5d. Exact-match amount verification (zero underpayment tolerance & no auto-surplus on VietQR)
        BigDecimal receivedNormalized =
                "VND".equalsIgnoreCase(tx.getCurrency())
                        ? tx.getTransferAmount().stripTrailingZeros()
                        : tx.getTransferAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedNormalized =
                "VND".equalsIgnoreCase(tx.getCurrency())
                        ? expectedAmount.setScale(0, RoundingMode.HALF_UP)
                        : expectedAmount.setScale(2, RoundingMode.HALF_UP);

        int cmp = receivedNormalized.compareTo(expectedNormalized);
        if (cmp < 0) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.AMOUNT_MISMATCH,
                    "UNDERPAID",
                    "Số tiền thực nhận ("
                            + receivedNormalized.toPlainString()
                            + " "
                            + tx.getCurrency()
                            + ") thiếu so với lệnh thu ("
                            + expectedNormalized.toPlainString()
                            + " "
                            + tx.getCurrency()
                            + "). Chuyển Finance đối soát.",
                    expectedNormalized);
        }
        if (cmp > 0) {
            return markException(
                    tx,
                    previousStatus,
                    SepayTransactionStatus.AMOUNT_MISMATCH,
                    "OVERPAID",
                    "Số tiền thực nhận ("
                            + receivedNormalized.toPlainString()
                            + " "
                            + tx.getCurrency()
                            + ") vượt quá lệnh thu ("
                            + expectedNormalized.toPlainString()
                            + " "
                            + tx.getCurrency()
                            + "). Chuyển Finance đối soát.",
                    expectedNormalized);
        }

        // 6. Exact match confirmed -> post payment into authoritative PaymentService ledger
        String processorRef = resolveProcessorReference(tx);
        try {
            paymentService.confirmPayment(
                    lockedPayment.getPaymentId(),
                    new PaymentConfirmRequest(lockedPayment.getRequestedAmount(), processorRef));
        } catch (BusinessException bizEx) {
            // confirmPayment runs in the shared transaction which Spring has already marked
            // rollback-only; rethrow a typed rejection so the caller persists RECONCILIATION_REQUIRED
            // in a fresh REQUIRES_NEW transaction without UnexpectedRollbackException.
            throw new PostingBusinessRejectionException(
                    bizEx.getCode() != null ? bizEx.getCode() : "CONFIRM_REJECTED",
                    "Không thể tự động ghi sổ khoản thu: " + bizEx.getMessage(),
                    lockedBooking != null ? lockedBooking.getBookingId() : null,
                    lockedPayment.getPaymentId(),
                    expectedNormalized,
                    bizEx);
        }

        Instant now = Instant.now();
        tx.setStatus(SepayTransactionStatus.PROCESSED);
        tx.setReconciliationStatus(SepayReconciliationStatus.NONE);
        tx.setExceptionCode(null);
        tx.setExceptionReason(null);
        tx.setLastError(null);
        tx.setMatchedAt(now);
        tx.setPostedAt(now);
        sepayTransactionRepository.save(tx);

        String auditAction =
                previousStatus == SepayTransactionStatus.RETRY_REQUIRED
                        ? "RETRY_SUCCEEDED"
                        : "AUTO_POSTED";
        recordAudit(
                tx,
                auditAction,
                previousStatus.name(),
                SepayTransactionStatus.PROCESSED.name(),
                null,
                "SYSTEM_SEPAY_WEBHOOK",
                "Khớp chính xác lệnh VietQR và ghi nhận khoản thu thành công",
                processorRef,
                lockedBooking != null ? lockedBooking.getBookingId() : null,
                lockedPayment.getPaymentId());

        String bookingRef =
                lockedBooking != null
                        ? lockedBooking.getBookingReference()
                        : lockedPayment.getInternalReceiptReference();
        log.info(
                "Posted exact-match SePay webhook sepayId={} to payment={} booking={}",
                tx.getSepayId(),
                lockedPayment.getPaymentId(),
                bookingRef);
        return SepayWebhookResponse.builder()
                .success(true)
                .message("Payment confirmed successfully")
                .paymentId(lockedPayment.getPaymentId())
                .bookingReference(bookingRef)
                .build();
    }

    public static class PostingBusinessRejectionException extends RuntimeException {
        private final String exceptionCode;
        private final String exceptionReason;
        private final UUID bookingId;
        private final UUID paymentId;
        private final BigDecimal expectedAmount;

        public PostingBusinessRejectionException(
                String exceptionCode,
                String exceptionReason,
                UUID bookingId,
                UUID paymentId,
                BigDecimal expectedAmount,
                Throwable cause) {
            super(exceptionReason, cause);
            this.exceptionCode = exceptionCode;
            this.exceptionReason = exceptionReason;
            this.bookingId = bookingId;
            this.paymentId = paymentId;
            this.expectedAmount = expectedAmount;
        }

        public String getExceptionCode() {
            return exceptionCode;
        }

        public String getExceptionReason() {
            return exceptionReason;
        }

        public UUID getBookingId() {
            return bookingId;
        }

        public UUID getPaymentId() {
            return paymentId;
        }

        public BigDecimal getExpectedAmount() {
            return expectedAmount;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SepayWebhookResponse markReconciliationFromRejection(
            UUID transactionId, PostingBusinessRejectionException rejection) {
        SepayTransaction tx =
                sepayTransactionRepository
                        .findByIdForUpdate(transactionId)
                        .or(() -> sepayTransactionRepository.findById(transactionId))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "SepayTransaction not found for reconciliation: "
                                                        + transactionId));
        SepayTransactionStatus previousStatus = tx.getStatus();
        if (rejection.getBookingId() != null && tx.getBooking() == null) {
            bookingRepository.findById(rejection.getBookingId()).ifPresent(tx::setBooking);
        }
        if (rejection.getPaymentId() != null && tx.getPayment() == null) {
            paymentRepository.findById(rejection.getPaymentId()).ifPresent(tx::setPayment);
        }
        tx.setLastAttemptAt(Instant.now());
        return markException(
                tx,
                previousStatus,
                SepayTransactionStatus.RECONCILIATION_REQUIRED,
                rejection.getExceptionCode(),
                rejection.getExceptionReason(),
                rejection.getExpectedAmount());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryRequired(UUID transactionId, String errorMessage) {
        sepayTransactionRepository
                .findByIdForUpdate(transactionId)
                .or(() -> sepayTransactionRepository.findById(transactionId))
                .ifPresent(
                        tx -> {
                            String prev =
                                    tx.getStatus() != null ? tx.getStatus().name() : null;
                            tx.setStatus(SepayTransactionStatus.RETRY_REQUIRED);
                            tx.setRetryCount((tx.getRetryCount() != null ? tx.getRetryCount() : 0) + 1);
                            tx.setLastError(errorMessage);
                            tx.setLastAttemptAt(Instant.now());
                            sepayTransactionRepository.save(tx);
                            recordAudit(
                                    tx,
                                    "POSTING_FAILED_RETRYABLE",
                                    prev,
                                    SepayTransactionStatus.RETRY_REQUIRED.name(),
                                    null,
                                    "SYSTEM_SEPAY_WEBHOOK",
                                    "Lỗi tạm thời khi ghi sổ; sự kiện được giữ lại để phục hồi an"
                                            + " toàn: "
                                            + errorMessage,
                                    tx.getReferenceCode(),
                                    tx.getBooking() != null ? tx.getBooking().getBookingId() : null,
                                    tx.getPayment() != null ? tx.getPayment().getPaymentId() : null);
                        });
    }

    private SepayWebhookResponse markException(
            SepayTransaction tx,
            SepayTransactionStatus previousStatus,
            SepayTransactionStatus exceptionStatus,
            String exceptionCode,
            String exceptionReason,
            BigDecimal expectedAmount) {
        tx.setStatus(exceptionStatus);
        tx.setExceptionCode(exceptionCode);
        tx.setExceptionReason(exceptionReason);
        if (expectedAmount != null) {
            tx.setExpectedAmount(expectedAmount);
        }
        tx.setReconciliationStatus(SepayReconciliationStatus.PENDING_REVIEW);
        tx.setLastError(null);
        sepayTransactionRepository.save(tx);

        recordAudit(
                tx,
                "MARKED_EXCEPTION",
                previousStatus != null ? previousStatus.name() : null,
                exceptionStatus.name(),
                null,
                "SYSTEM_SEPAY_WEBHOOK",
                exceptionReason,
                tx.getReferenceCode(),
                tx.getBooking() != null ? tx.getBooking().getBookingId() : null,
                tx.getPayment() != null ? tx.getPayment().getPaymentId() : null);

        log.warn(
                "SePay webhook sepayId={} routed to Finance reconciliation: code={}, reason={}",
                tx.getSepayId(),
                exceptionCode,
                exceptionReason);

        return SepayWebhookResponse.builder()
                .success(true)
                .message("Recorded for Finance reconciliation (" + exceptionCode + "): " + exceptionReason)
                .paymentId(tx.getPayment() != null ? tx.getPayment().getPaymentId() : null)
                .bookingReference(
                        tx.getBooking() != null ? tx.getBooking().getBookingReference() : null)
                .build();
    }

    private Payment findMatchingPayment(String fullMemo) {
        if (fullMemo == null || fullMemo.isBlank()) {
            return null;
        }
        String cleanMemo = fullMemo.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (cleanMemo.isBlank()) {
            return null;
        }

        // First search Pending SePay orders (most specific receipt reference first)
        List<Payment> pendingPayments =
                paymentRepository.findByStatusAndPaymentMethod(
                        PaymentStatus.Pending, PaymentMethod.Sepay);
        Payment matchedPending = matchPaymentList(pendingPayments, cleanMemo);
        if (matchedPending != null) {
            return matchedPending;
        }

        // Fallback: search historical SePay orders (Confirmed / Cancelled / Failed) to detect duplicate/late transfers
        List<Payment> allSepayPayments = paymentRepository.findByPaymentMethod(PaymentMethod.Sepay);
        return matchPaymentList(allSepayPayments, cleanMemo);
    }

    private Payment matchPaymentList(List<Payment> candidates, String cleanMemo) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Payment> sorted =
                candidates.stream()
                        .filter(
                                p ->
                                        p.getInternalReceiptReference() != null
                                                && !p.getInternalReceiptReference().isBlank())
                        .sorted(
                                Comparator.comparingInt(
                                                (Payment p) ->
                                                        p.getInternalReceiptReference()
                                                                .replaceAll("[^A-Za-z0-9]", "")
                                                                .length())
                                        .reversed()
                                        .thenComparing(
                                                Payment::getRequestedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();

        for (Payment p : sorted) {
            String cleanReceipt =
                    p.getInternalReceiptReference()
                            .replaceAll("[^A-Za-z0-9]", "")
                            .toUpperCase(Locale.ROOT);
            if (!cleanReceipt.isBlank() && cleanMemo.contains(cleanReceipt)) {
                return p;
            }
        }
        for (Payment p : candidates) {
            if (p.getIdempotencyKey() != null && !p.getIdempotencyKey().isBlank()) {
                String cleanKey =
                        p.getIdempotencyKey()
                                .replaceAll("[^A-Za-z0-9]", "")
                                .toUpperCase(Locale.ROOT);
                if (!cleanKey.isBlank() && cleanMemo.contains(cleanKey)) {
                    return p;
                }
            }
        }
        return null;
    }

    private Booking findMatchingBooking(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        Matcher matcher = BOOKING_REF_PATTERN.matcher(memo);
        while (matcher.find()) {
            String candidate = matcher.group().toUpperCase(Locale.ROOT);
            Optional<Booking> direct = bookingRepository.findByBookingReference(candidate);
            if (direct.isPresent()) {
                return direct.get();
            }
            String cleanDigits = candidate.replaceAll("[^0-9]", "");
            if (cleanDigits.length() == 4) {
                Optional<Booking> std =
                        bookingRepository.findByBookingReference("BK-" + cleanDigits);
                if (std.isPresent()) {
                    return std.get();
                }
            }
            if (cleanDigits.length() >= 8) {
                String legacy =
                        "BK-" + cleanDigits.substring(0, 4) + "-" + cleanDigits.substring(4);
                Optional<Booking> norm = bookingRepository.findByBookingReference(legacy);
                if (norm.isPresent()) {
                    return norm.get();
                }
            }
        }
        String cleanMemo = memo.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!cleanMemo.isBlank()) {
            List<Booking> allBookings = bookingRepository.findAll();
            return allBookings.stream()
                    .filter(
                            b ->
                                    b.getBookingReference() != null
                                            && !b.getBookingReference().isBlank())
                    .sorted(
                            Comparator.comparingInt(
                                            (Booking b) ->
                                                    b.getBookingReference()
                                                            .replaceAll("[^A-Za-z0-9]", "")
                                                            .length())
                                    .reversed())
                    .filter(
                            b -> {
                                String cleanRef =
                                        b.getBookingReference()
                                                .replaceAll("[^A-Za-z0-9]", "")
                                                .toUpperCase(Locale.ROOT);
                                return cleanRef.length() >= 5 && cleanMemo.contains(cleanRef);
                            })
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private BigDecimal resolveExpectedWebhookAmount(
            Payment payment, Booking booking, String webhookCurrency) {
        if (payment == null || payment.getRequestedAmount() == null) {
            return BigDecimal.ZERO;
        }
        boolean paymentIsVnd =
                "VND".equalsIgnoreCase(payment.getCurrency())
                        || (booking != null && "VND".equalsIgnoreCase(booking.getCurrency()));
        if (paymentIsVnd) {
            return payment.getRequestedAmount().setScale(0, RoundingMode.HALF_UP);
        }
        if ("USD".equalsIgnoreCase(webhookCurrency)) {
            return payment.getRequestedAmount().setScale(2, RoundingMode.HALF_UP);
        }
        // Legacy USD booking paid via VND VietQR
        if (payment.getOriginalAmount() != null
                && "VND".equalsIgnoreCase(payment.getOriginalCurrency())) {
            return payment.getOriginalAmount().setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal rate =
                payment.getExchangeRateApplied() != null
                                && payment.getExchangeRateApplied().signum() > 0
                        ? payment.getExchangeRateApplied()
                        : (sepayProperties.getUsdToVndRate() != null
                                        && sepayProperties.getUsdToVndRate().signum() > 0
                                ? sepayProperties.getUsdToVndRate()
                                : BigDecimal.valueOf(25400));
        return payment.getRequestedAmount().multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }

    private String resolveProcessorReference(SepayTransaction tx) {
        if (tx.getSepayId() != null) {
            return "SEPAY-" + tx.getSepayId();
        }
        if (tx.getReferenceCode() != null
                && !tx.getReferenceCode().isBlank()
                && !"0".equals(tx.getReferenceCode().trim())) {
            return tx.getReferenceCode().trim();
        }
        return "SEPAY-TX-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildFullMemo(SepayTransaction tx) {
        StringBuilder sb = new StringBuilder();
        if (tx.getCode() != null) sb.append(tx.getCode()).append(' ');
        if (tx.getContent() != null) sb.append(tx.getContent()).append(' ');
        if (tx.getDescription() != null) sb.append(tx.getDescription());
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "VND";
        }
        String up = raw.trim().toUpperCase(Locale.ROOT);
        return "VNĐ".equals(up) ? "VND" : up;
    }

    private String normalizeDigits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private Instant parseTransactionDate(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(raw.trim(), SEPAY_DATE_FORMATTER);
                return ldt.toInstant(ZoneOffset.ofHours(7));
            } catch (Exception ignored) {
                // fallback below
            }
        }
        return Instant.now();
    }

    void recordAudit(
            SepayTransaction tx,
            String action,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorUsername,
            String reason,
            String evidenceRef,
            UUID relatedBookingId,
            UUID relatedPaymentId) {
        if (auditRepository == null || tx == null || tx.getId() == null) {
            return;
        }
        SepayReconciliationAudit entry = new SepayReconciliationAudit();
        entry.setSepayTransaction(tx);
        entry.setAction(action);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setActorUserId(actorUserId);
        entry.setActorUsername(actorUsername);
        entry.setReason(reason);
        entry.setEvidenceReference(evidenceRef);
        entry.setRelatedBookingId(relatedBookingId);
        entry.setRelatedPaymentId(relatedPaymentId);
        entry.setCreatedAt(Instant.now());
        auditRepository.save(entry);
    }
}
