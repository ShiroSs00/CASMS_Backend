package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.PaymentCreateRequest;
import com.casms.payment.dto.response.PaymentResponse;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        log.info("Processing payment creation for booking {} with idempotency key {}",
                request.getBookingId(), request.getIdempotencyKey());

        // 1. Idempotency check - if duplicate key found, return existing record
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            log.info("Returning existing payment for idempotency key: {}", request.getIdempotencyKey());
            return toResponse(existingPayment.get());
        }

        // 2. Validate booking existence and status
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Cannot initiate payment for a cancelled booking");
        }

        // 3. Create payment record
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setRequestedAmount(request.getRequestedAmount());
        payment.setCurrency(request.getCurrency() != null ? request.getCurrency() : booking.getCurrency());
        payment.setRequestedAt(Instant.now());
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setInternalReceiptReference("RCP-" + System.currentTimeMillis());

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse confirmPayment(UUID paymentId, PaymentConfirmRequest request) {
        log.info("Confirming payment {} with amount {}", paymentId, request.getConfirmedAmount());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        // Idempotent confirmation
        if (payment.getStatus() == PaymentStatus.Confirmed) {
            log.info("Payment {} is already confirmed", paymentId);
            return toResponse(payment);
        }

        payment.setStatus(PaymentStatus.Confirmed);
        payment.setConfirmedAmount(request.getConfirmedAmount());
        payment.setConfirmedAt(Instant.now());
        if (request.getProcessorReference() != null) {
            payment.setProcessorReference(request.getProcessorReference());
        }

        Payment updated = paymentRepository.save(payment);

        // Auto-confirm booking if total confirmed amount covers the agreed fare
        Booking booking = updated.getBooking();
        if (booking != null && booking.getBookingStatus() != BookingStatus.Cancelled) {
            List<Payment> bookingPayments = paymentRepository.findByBooking_BookingId(booking.getBookingId());
            BigDecimal totalConfirmed = bookingPayments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.Confirmed)
                    .map(p -> p.getConfirmedAmount() != null ? p.getConfirmedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalConfirmed.compareTo(booking.getAgreedFareAmount()) >= 0
                    || totalConfirmed.compareTo(booking.getRequiredDepositAmount()) >= 0) {
                if (booking.getBookingStatus() == BookingStatus.Draft) {
                    booking.setBookingStatus(BookingStatus.Confirmed);
                    bookingRepository.save(booking);
                    log.info("Booking {} successfully transitioned to Confirmed after payment", booking.getBookingId());
                }
            }
        }

        return toResponse(updated);
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

        return PaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .bookingId(bId)
                .bookingReference(bRef)
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
