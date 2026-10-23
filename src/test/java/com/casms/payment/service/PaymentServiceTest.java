package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.PaymentCreateRequest;
import com.casms.payment.dto.response.PaymentResponse;
import com.casms.payment.entity.Payment;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.InstallmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID bookingId;
    private UUID paymentId;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingReference("BK-2026-0001");
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setAgreedFareAmount(new BigDecimal("10000000"));
        booking.setRequiredDepositAmount(new BigDecimal("3000000"));
        booking.setCurrency("VND");

        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setBooking(booking);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.GatewaySandbox);
        payment.setRequestedAmount(new BigDecimal("10000000"));
        payment.setCurrency("VND");
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey("KEY-123456");
    }

    @Test
    @DisplayName("Khởi tạo thanh toán thành công với mã Idempotency mới")
    void createPayment_Success() {
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId)).thenReturn(List.of());
        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("10000000"));
        req.setPaymentMethod(PaymentMethod.GatewaySandbox);
        req.setIdempotencyKey("KEY-123456");

        when(paymentRepository.findByIdempotencyKey("KEY-123456")).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentResponse resp = paymentService.createPayment(req);

        assertNotNull(resp);
        assertEquals(PaymentStatus.Pending, resp.getStatus());
        assertEquals("KEY-123456", resp.getIdempotencyKey());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Bảo đảm Idempotent: Gửi lại cùng idempotencyKey trả về giao dịch cũ, không tạo lặp")
    void createPayment_DuplicateIdempotencyKey_ReturnsExisting() {
        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("10000000"));
        req.setPaymentMethod(PaymentMethod.GatewaySandbox);
        req.setIdempotencyKey("KEY-123456");

        when(paymentRepository.findByIdempotencyKey("KEY-123456")).thenReturn(Optional.of(payment));

        PaymentResponse resp = paymentService.createPayment(req);

        assertNotNull(resp);
        assertEquals(paymentId, resp.getPaymentId());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xác nhận thanh toán không tự chuyển booking sang Confirmed")
    void confirmPayment_DoesNotAutoConfirmBooking() {
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId)).thenReturn(List.of());
        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setConfirmedAmount(new BigDecimal("10000000"));
        req.setProcessorReference("TXN-PAYPAL-9999");

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        Payment confirmedPayment = new Payment();
        confirmedPayment.setPaymentId(paymentId);
        confirmedPayment.setBooking(booking);
        confirmedPayment.setStatus(PaymentStatus.Confirmed);
        confirmedPayment.setConfirmedAmount(new BigDecimal("10000000"));
        confirmedPayment.setConfirmedAt(Instant.now());

        when(paymentRepository.save(any(Payment.class))).thenReturn(confirmedPayment);
        PaymentResponse resp = paymentService.confirmPayment(paymentId, req);

        assertNotNull(resp);
        assertEquals(PaymentStatus.Confirmed, resp.getStatus());
        assertEquals(BookingStatus.Draft, booking.getBookingStatus());
        assertEquals("TXN-PAYPAL-9999", payment.getProcessorReference());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmPayment_RejectsAmountAboveRequested() {
        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setConfirmedAmount(new BigDecimal("10000001"));
        req.setProcessorReference("TXN-OVER");
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.confirmPayment(paymentId, req));

        assertEquals("INVALID_CONFIRMED_AMOUNT", ex.getCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void confirmPayment_RejectsCancelledBooking() {
        booking.setBookingStatus(BookingStatus.Cancelled);
        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setConfirmedAmount(new BigDecimal("10000000"));
        req.setProcessorReference("TXN-CANCELLED");
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.confirmPayment(paymentId, req));

        assertEquals("BOOKING_CANCELLED", ex.getCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Ném BusinessException khi thanh toán cho booking đã bị hủy")
    void createPayment_CancelledBooking_ThrowsException() {
        booking.setBookingStatus(BookingStatus.Cancelled);

        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("5000000"));
        req.setPaymentMethod(PaymentMethod.Cash);
        req.setIdempotencyKey("KEY-CANCELLED");

        when(paymentRepository.findByIdempotencyKey("KEY-CANCELLED")).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(req));

        assertEquals("BOOKING_CANCELLED", ex.getCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void passengerCanReadOwnBookingTransactions() {
        User owner = new User();
        owner.setUserId(UUID.randomUUID());
        booking.setBookedByUser(owner);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_BookingId(bookingId)).thenReturn(List.of(payment));

        assertEquals(1, paymentService.getPaymentsForBooking(bookingId, owner.getUserId(), false).size());
    }

    @Test
    void passengerCannotReadAnotherUsersTransactions() {
        User owner = new User();
        owner.setUserId(UUID.randomUUID());
        booking.setBookedByUser(owner);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () ->
                paymentService.getPaymentsForBooking(bookingId, UUID.randomUUID(), false));
        verify(paymentRepository, never()).findByBooking_BookingId(any());
    }
}
