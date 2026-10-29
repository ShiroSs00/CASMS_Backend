package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.PaymentCreateRequest;
import com.casms.payment.dto.response.PaymentResponse;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final InstallmentRepository installmentRepository;
    private final BookingService bookingService;

    @Transactional
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        log.info("Processing payment creation for booking {} with idempotency key {}",
                request.getBookingId(), request.getIdempotencyKey());

        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency key is required");
        }
        String idempotencyKey = request.getIdempotencyKey().trim();

        if (request.getRequestedAmount() == null || request.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUESTED_AMOUNT",
                    "Requested amount must be strictly positive");
        }

        // 1. Idempotency check - if duplicate key found, verify payload or return existing record
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            boolean bookingMatches = existing.getBooking() != null && existing.getBooking().getBookingId().equals(request.getBookingId());
            boolean amountMatches = existing.getRequestedAmount() != null
                    && existing.getRequestedAmount().compareTo(request.getRequestedAmount()) == 0;
            String reqCurrency = request.getCurrency() != null ? request.getCurrency().trim()
                    : (existing.getBooking() != null ? existing.getBooking().getCurrency() : null);
            boolean currencyMatches = reqCurrency == null
                    || (existing.getCurrency() != null && existing.getCurrency().equalsIgnoreCase(reqCurrency));
            boolean installmentMatches = true;
            if (request.getInstallmentId() != null) {
                installmentMatches = existing.getInstallment() != null
                        && request.getInstallmentId().equals(existing.getInstallment().getInstallmentId());
            }

            if (!bookingMatches || !amountMatches || !currencyMatches || !installmentMatches) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "Idempotency key already exists with different payment parameters");
            }

            log.info("Returning existing payment for idempotency key: {}", idempotencyKey);
            return toResponse(existing);
        }

        // 2. Validate booking existence and status
        com.casms.common.util.NewWriteCurrency.requireUsd(request.getCurrency());
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot initiate payment for a cancelled booking");
        }
        if (booking.getBookingStatus() == BookingStatus.Draft && booking.getHoldExpiresAt() != null
                && Instant.now().isAfter(booking.getHoldExpiresAt())) {
            throw new BusinessException("BOOKING_HOLD_EXPIRED", "Booking hold has expired. Please create a new booking.");
        }

        String currency = request.getCurrency() != null ? request.getCurrency().trim() : booking.getCurrency();
        if (booking.getCurrency() == null || currency == null
                || !currency.equalsIgnoreCase(booking.getCurrency().trim())) {
            throw new BusinessException("PAYMENT_CURRENCY_MISMATCH",
                    "Payment currency must match the booking currency");
        }

        // 3. Outstanding balance validation (total agreed fare - total confirmed collect payments)
        BigDecimal totalConfirmed = paymentRepository.findForBooking(booking.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingBalance = booking.getAgreedFareAmount() != null
                ? booking.getAgreedFareAmount().subtract(totalConfirmed)
                : BigDecimal.ZERO;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
            outstandingBalance = BigDecimal.ZERO;
        }

        if (request.getRequestedAmount().compareTo(outstandingBalance) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_OUTSTANDING_BALANCE",
                    "Requested amount " + request.getRequestedAmount() + " exceeds outstanding balance " + outstandingBalance);
        }

        // 4. Create payment record and associate installment
        Payment payment = new Payment();
        payment.setBooking(booking);

        Installment targetInstallment = null;
        if (request.getInstallmentId() != null) {
            targetInstallment = installmentRepository.findById(request.getInstallmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Installment not found with id: " + request.getInstallmentId()));
            if (!targetInstallment.getBooking().getBookingId().equals(booking.getBookingId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_BOOKING_MISMATCH",
                        "The specified installment does not belong to the booking");
            }
            final UUID targetInstId = targetInstallment.getInstallmentId();
            BigDecimal confirmedForInst = paymentRepository.findForBooking(booking.getBookingId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                            && p.getOperationType() == PaymentOperationType.Collect
                            && p.getInstallment() != null
                            && p.getInstallment().getInstallmentId().equals(targetInstId)
                            && p.getConfirmedAmount() != null)
                    .map(Payment::getConfirmedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainingForInst = targetInstallment.getRequiredAmount().subtract(confirmedForInst);
            if (remainingForInst.compareTo(BigDecimal.ZERO) < 0) {
                remainingForInst = BigDecimal.ZERO;
            }
            if (request.getRequestedAmount().compareTo(remainingForInst) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_INSTALLMENT",
                        "Requested amount " + request.getRequestedAmount() + " exceeds remaining amount for target installment " + remainingForInst);
            }
        } else {
            List<Installment> insts = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId());
            for (Installment inst : insts) {
                final UUID instId = inst.getInstallmentId();
                BigDecimal confirmedForInst = paymentRepository.findForBooking(booking.getBookingId()).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                                && p.getOperationType() == PaymentOperationType.Collect
                                && p.getInstallment() != null
                                && p.getInstallment().getInstallmentId().equals(instId)
                                && p.getConfirmedAmount() != null)
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (confirmedForInst.compareTo(inst.getRequiredAmount()) < 0) {
                    targetInstallment = inst;
                    break;
                }
            }
            if (targetInstallment == null && !insts.isEmpty()) {
                targetInstallment = insts.get(insts.size() - 1);
            }
        }
        payment.setInstallment(targetInstallment);

        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setRequestedAmount(request.getRequestedAmount());
        payment.setCurrency(currency.toUpperCase(Locale.ROOT));
        payment.setRequestedAt(Instant.now());
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey(idempotencyKey);
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse confirmPayment(UUID paymentId, PaymentConfirmRequest request) {
        log.info("Confirming payment {} with amount {}", paymentId, request.getConfirmedAmount());

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        // Idempotent confirmation
        if (payment.getStatus() == PaymentStatus.Confirmed) {
            log.info("Payment {} is already confirmed", paymentId);
            return toResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.Pending) {
            throw new BusinessException("PAYMENT_NOT_PENDING", "Only pending payments can be confirmed");
        }
        if (payment.getOperationType() != PaymentOperationType.Collect || payment.getBooking() == null) {
            throw new BusinessException("PAYMENT_NOT_BOOKING_COLLECTION",
                    "Only booking collection payments can be confirmed through this endpoint");
        }
        if (payment.getBooking().getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot confirm payment for a cancelled booking");
        }
        if (payment.getBooking().getBookingStatus() == BookingStatus.Draft
                && payment.getBooking().getHoldExpiresAt() != null
                && Instant.now().isAfter(payment.getBooking().getHoldExpiresAt())) {
            throw new BusinessException("BOOKING_HOLD_EXPIRED", "Booking hold has expired and cannot be confirmed");
        }
        BigDecimal confirmedAmount = request.getConfirmedAmount();
        if (confirmedAmount == null || confirmedAmount.signum() <= 0
                || payment.getRequestedAmount() == null
                || confirmedAmount.compareTo(payment.getRequestedAmount()) > 0) {
            throw new BusinessException("INVALID_CONFIRMED_AMOUNT",
                    "Confirmed amount must be positive and cannot exceed the requested payment amount");
        }
        if (payment.getCurrency() == null || payment.getBooking().getCurrency() == null
                || !payment.getCurrency().trim().equalsIgnoreCase(payment.getBooking().getCurrency().trim())) {
            throw new BusinessException("PAYMENT_CURRENCY_MISMATCH",
                    "Payment currency must match the booking currency");
        }
        if (request.getProcessorReference() == null || request.getProcessorReference().isBlank()) {
            throw new BusinessException("PAYMENT_EVIDENCE_REQUIRED",
                    "A processor or cash receipt reference is required to confirm payment");
        }

        String ref = request.getProcessorReference().trim();
        if (payment.getPaymentMethod() == PaymentMethod.Cash) {
            if (paymentRepository.existsByInternalReceiptReferenceAndPaymentIdNot(ref, paymentId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_REFERENCE",
                        "Cash receipt reference has already been used by another transaction");
            }
            payment.setInternalReceiptReference(ref);
        } else {
            if (paymentRepository.existsByProcessorReferenceAndPaymentIdNot(ref, paymentId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_REFERENCE",
                        "Payment processor reference has already been used by another transaction");
            }
            payment.setProcessorReference(ref);
        }

        payment.setStatus(PaymentStatus.Confirmed);
        payment.setConfirmedAmount(confirmedAmount);
        payment.setConfirmedAt(Instant.now());

        Payment updated = paymentRepository.save(payment);

        // Synchronize installments and auto-confirm booking if deposit met & contract accepted
        if (updated.getBooking() != null) {
            allocateInstallmentsAndSync(updated.getBooking());
        }

        return toResponse(updated);
    }

    /**
     * BE-16: Ghi nhận trạng thái thanh toán thất bại (Failed) có Idempotent.
     */
    @Transactional
    public PaymentResponse failPayment(UUID paymentId, String reason) {
        log.warn("Recording payment failure for {} with reason: {}", paymentId, reason);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (payment.getStatus() == PaymentStatus.Failed) {
            log.info("Payment {} is already marked as Failed", paymentId);
            return toResponse(payment); // Idempotent retry
        }

        if (payment.getStatus() == PaymentStatus.Confirmed) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CONFIRMED",
                    "Cannot mark an already confirmed payment as failed");
        }

        payment.setStatus(PaymentStatus.Failed);
        payment.setFailureReason(reason != null && !reason.isBlank() ? reason.trim() : "Payment processing failed");
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    private void allocateInstallmentsAndSync(Booking booking) {
        List<Installment> installments = installmentRepository
                .findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId());
        if (installments.isEmpty()) return;

        List<Payment> confirmedPayments = paymentRepository.findForBooking(booking.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null
                        && p.getConfirmedAmount().signum() > 0)
                .sorted(Comparator.comparing(Payment::getConfirmedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Payment::getRequestedAt))
                .toList();

        int instIdx = 0;
        BigDecimal allocatedForCurrentInst = BigDecimal.ZERO;
        for (Payment p : confirmedPayments) {
            if (p.getInstallment() == null && instIdx < installments.size()) {
                p.setInstallment(installments.get(instIdx));
                paymentRepository.save(p);
            }
            if (p.getConfirmedAmount() != null) {
                BigDecimal remainingFromPayment = p.getConfirmedAmount();
                while (instIdx < installments.size() && remainingFromPayment.signum() > 0) {
                    BigDecimal neededForCurrent = installments.get(instIdx).getRequiredAmount().subtract(allocatedForCurrentInst);
                    if (neededForCurrent.compareTo(BigDecimal.ZERO) <= 0) {
                        instIdx++;
                        allocatedForCurrentInst = BigDecimal.ZERO;
                        continue;
                    }
                    if (remainingFromPayment.compareTo(neededForCurrent) >= 0) {
                        remainingFromPayment = remainingFromPayment.subtract(neededForCurrent);
                        instIdx++;
                        allocatedForCurrentInst = BigDecimal.ZERO;
                    } else {
                        allocatedForCurrentInst = allocatedForCurrentInst.add(remainingFromPayment);
                        remainingFromPayment = BigDecimal.ZERO;
                    }
                }
            }
        }

        // Auto-confirm booking if deposit requirement is satisfied and contract is Accepted!
        bookingService.tryAutoConfirm(booking);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForBooking(UUID bookingId, UUID userId, boolean canReadAll) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        assertPaymentReadAccess(booking, userId, canReadAll);
        return paymentRepository.findByBooking_BookingId(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetail(UUID paymentId, UUID userId, boolean canReadAll) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
        if (payment.getBooking() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Payment is not associated with an accessible booking");
        }
        assertPaymentReadAccess(payment.getBooking(), userId, canReadAll);
        return toResponse(payment);
    }

    private void assertPaymentReadAccess(Booking booking, UUID userId, boolean canReadAll) {
        if (!canReadAll && (userId == null || booking.getBookedByUser() == null
                || !userId.equals(booking.getBookedByUser().getUserId()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view transactions for your own booking");
        }
    }

    private PaymentResponse toResponse(Payment p) {
        UUID bId = p.getBooking() != null ? p.getBooking().getBookingId() : null;
        String bRef = p.getBooking() != null ? p.getBooking().getBookingReference() : null;
        UUID instId = p.getInstallment() != null ? p.getInstallment().getInstallmentId() : null;
        Integer seqNo = p.getInstallment() != null ? p.getInstallment().getSequenceNo() : null;

        return PaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .bookingId(bId)
                .bookingReference(bRef)
                .installmentId(instId)
                .sequenceNo(seqNo)
                .operationType(p.getOperationType())
                .paymentMethod(p.getPaymentMethod())
                .requestedAmount(p.getRequestedAmount())
                .confirmedAmount(p.getConfirmedAmount())
                .currency(p.getCurrency())
                .requestedAt(p.getRequestedAt())
                .confirmedAt(p.getConfirmedAt())
                .processorReference(p.getProcessorReference())
                .internalReceiptReference(p.getInternalReceiptReference())
                .status(p.getStatus())
                .failureReason(p.getFailureReason())
                .idempotencyKey(p.getIdempotencyKey())
                .build();
    }
}
