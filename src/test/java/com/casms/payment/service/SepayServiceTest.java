package com.casms.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.InstallmentType;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.PaymentType;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.SepayTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SepayServiceTest {

    @Mock private SepayProperties sepayProperties;

    @Mock private PaymentRepository paymentRepository;

    @Mock private BookingRepository bookingRepository;

    @Mock private InstallmentRepository installmentRepository;

    @Mock private SepayTransactionRepository sepayTransactionRepository;

    @Mock private PaymentService paymentService;

    @Mock private BookingService bookingService;

    @InjectMocks private SepayService sepayService;

    private UUID bookingId;
    private UUID installment1Id;
    private UUID installment2Id;
    private Booking booking;
    private Installment inst1;
    private Installment inst2;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        installment1Id = UUID.randomUUID();
        installment2Id = UUID.randomUUID();

        booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingReference("BK-2026-0001");
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setAgreedFareAmount(new BigDecimal("1000.0000"));
        booking.setRequiredDepositAmount(new BigDecimal("300.0000"));
        booking.setCurrency("USD");

        inst1 = new Installment();
        inst1.setInstallmentId(installment1Id);
        inst1.setBooking(booking);
        inst1.setSequenceNo(1);
        inst1.setInstallmentType(InstallmentType.Deposit);
        inst1.setRequiredAmount(new BigDecimal("300.0000"));
        inst1.setCurrency("USD");
        inst1.setStatus(InstallmentStatus.Scheduled);
        inst1.setDueAt(Instant.now().plusSeconds(86400));

        inst2 = new Installment();
        inst2.setInstallmentId(installment2Id);
        inst2.setBooking(booking);
        inst2.setSequenceNo(2);
        inst2.setInstallmentType(InstallmentType.FinalFare);
        inst2.setRequiredAmount(new BigDecimal("700.0000"));
        inst2.setCurrency("USD");
        inst2.setStatus(InstallmentStatus.Scheduled);
        inst2.setDueAt(Instant.now().plusSeconds(86400 * 30));
    }

    @Test
    @DisplayName("Tra cứu 3 phương án thanh toán (FULL, DEPOSIT, STAGE) thành công")
    void getPaymentOptions_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));

        BookingPaymentOptionsResponse resp = sepayService.getPaymentOptions(bookingId, null, true);

        assertNotNull(resp);
        assertEquals(bookingId, resp.getBookingId());
        assertEquals("BK-2026-0001", resp.getBookingReference());
        assertEquals(new BigDecimal("1000.0000"), resp.getAgreedFareAmount());
        assertEquals(new BigDecimal("1000.0000"), resp.getOutstandingBalance());
        assertEquals(new BigDecimal("25400000"), resp.getOutstandingBalanceVnd());

        // Kiểm tra phương án 1: FULL
        assertNotNull(resp.getFullPaymentOption());
        assertTrue(resp.getFullPaymentOption().isAvailable());
        assertEquals(new BigDecimal("1000.0000"), resp.getFullPaymentOption().getAmountUsd());
        assertEquals(new BigDecimal("25400000"), resp.getFullPaymentOption().getAmountVnd());

        // Kiểm tra phương án 2: DEPOSIT
        assertNotNull(resp.getDepositPaymentOption());
        assertTrue(resp.getDepositPaymentOption().isAvailable());
        assertFalse(resp.getDepositPaymentOption().isPaid());
        assertEquals(
                new BigDecimal("300.0000"), resp.getDepositPaymentOption().getRemainingAmountUsd());
        assertEquals(
                new BigDecimal("7620000"), resp.getDepositPaymentOption().getRemainingAmountVnd());

        // Kiểm tra phương án 3: STAGE
        assertNotNull(resp.getStagePaymentOption());
        assertEquals(2, resp.getStagePaymentOption().getTotalStages());
        assertEquals(2, resp.getStagePaymentOption().getStages().size());
        assertEquals(1, resp.getStagePaymentOption().getStages().get(0).getSequenceNo());
        assertTrue(resp.getStagePaymentOption().getStages().get(0).isCurrentDue());
    }

    @Test
    @DisplayName("Tạo Checkout SePay thành công với hình thức FULL (100% toàn bộ)")
    void createCheckout_Full_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getAccountName()).thenReturn("CASMS CRUISE");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByBooking_BookingId(bookingId)).thenReturn(List.of());

        Payment savedPayment = new Payment();
        savedPayment.setPaymentId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.Pending);
        savedPayment.setRequestedAt(Instant.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        SepayCheckoutRequest req = new SepayCheckoutRequest();
        req.setBookingId(bookingId);
        req.setPaymentType(PaymentType.FULL);

        SepayCheckoutResponse resp = sepayService.createCheckout(req, null, true);

        assertNotNull(resp);
        assertEquals(bookingId, resp.getBookingId());
        assertEquals(PaymentType.FULL, resp.getPaymentType());
        assertEquals(new BigDecimal("1000.0000"), resp.getAmountUsd());
        assertEquals(new BigDecimal("25400000"), resp.getAmountVnd());
        assertTrue(resp.getTransferContent().contains("CASMS"));
        assertTrue(resp.getQrCodeUrl().contains("qr.sepay.vn"));
        assertTrue(resp.getQrCodeUrl().contains("0901452366"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Tái sử dụng Payment Pending hiện có khi gọi lại checkout cho cùng booking và đợt")
    void createCheckout_ReuseExistingPendingPayment_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getAccountName()).thenReturn("CASMS CRUISE");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        UUID existingPaymentId = UUID.randomUUID();
        Payment existingPending = new Payment();
        existingPending.setPaymentId(existingPaymentId);
        existingPending.setBooking(booking);
        existingPending.setInstallment(inst1);
        existingPending.setStatus(PaymentStatus.Pending);
        existingPending.setPaymentMethod(PaymentMethod.Sepay);
        existingPending.setRequestedAmount(new BigDecimal("300.0000"));
        existingPending.setRequestedAt(Instant.now().minusSeconds(30));

        when(paymentRepository.findByBooking_BookingId(bookingId))
                .thenReturn(List.of(existingPending));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        SepayCheckoutRequest req = new SepayCheckoutRequest();
        req.setBookingId(bookingId);
        req.setPaymentType(PaymentType.DEPOSIT);

        SepayCheckoutResponse resp = sepayService.createCheckout(req, null, true);

        assertNotNull(resp);
        assertEquals(existingPaymentId, resp.getPaymentId());
        assertEquals(PaymentStatus.Pending, resp.getStatus());
        verify(paymentRepository).save(existingPending);
    }

    @Test
    @DisplayName("Tạo Checkout SePay thành công với hình thức DEPOSIT (đặt cọc)")
    void createCheckout_Deposit_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getAccountName()).thenReturn("CASMS CRUISE");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByBooking_BookingId(bookingId)).thenReturn(List.of());

        Payment savedPayment = new Payment();
        savedPayment.setPaymentId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.Pending);
        savedPayment.setRequestedAt(Instant.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        SepayCheckoutRequest req = new SepayCheckoutRequest();
        req.setBookingId(bookingId);
        req.setPaymentType(PaymentType.DEPOSIT);

        SepayCheckoutResponse resp = sepayService.createCheckout(req, null, true);

        assertNotNull(resp);
        assertEquals(PaymentType.DEPOSIT, resp.getPaymentType());
        assertEquals(installment1Id, resp.getInstallmentId());
        assertEquals(1, resp.getSequenceNo());
        assertEquals(new BigDecimal("300.0000"), resp.getAmountUsd());
        assertEquals(new BigDecimal("7620000"), resp.getAmountVnd());
        assertTrue(resp.getTransferContent().contains("BK20260001S1"));
    }

    @Test
    @DisplayName("Tạo Checkout SePay thành công với hình thức STAGE (theo đợt 2)")
    void createCheckout_Stage_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getAccountName()).thenReturn("CASMS CRUISE");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of());
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByBooking_BookingId(bookingId)).thenReturn(List.of());

        Payment savedPayment = new Payment();
        savedPayment.setPaymentId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.Pending);
        savedPayment.setRequestedAt(Instant.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        SepayCheckoutRequest req = new SepayCheckoutRequest();
        req.setBookingId(bookingId);
        req.setPaymentType(PaymentType.STAGE);
        req.setSequenceNo(2);

        SepayCheckoutResponse resp = sepayService.createCheckout(req, null, true);

        assertNotNull(resp);
        assertEquals(PaymentType.STAGE, resp.getPaymentType());
        assertEquals(installment2Id, resp.getInstallmentId());
        assertEquals(2, resp.getSequenceNo());
        assertEquals(new BigDecimal("700.0000"), resp.getAmountUsd());
        assertEquals(new BigDecimal("17780000"), resp.getAmountVnd());
    }

    @Test
    @DisplayName("Tạo Checkout DEPOSIT thất bại nếu tiền cọc đã được trả đủ")
    void createCheckout_Deposit_AlreadyPaid_ThrowsException() {
        Payment paidDeposit = new Payment();
        paidDeposit.setStatus(PaymentStatus.Confirmed);
        paidDeposit.setOperationType(PaymentOperationType.Collect);
        paidDeposit.setInstallment(inst1);
        paidDeposit.setConfirmedAmount(new BigDecimal("300.0000"));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(paidDeposit));
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));

        SepayCheckoutRequest req = new SepayCheckoutRequest();
        req.setBookingId(bookingId);
        req.setPaymentType(PaymentType.DEPOSIT);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> sepayService.createCheckout(req, null, true));
        assertEquals("DEPOSIT_ALREADY_PAID", ex.getCode());
    }

    @Test
    @DisplayName(
            "Xử lý Webhook SePay thành công: khớp payment, gọi confirmPayment và cập nhật"
                + " PROCESSED")
    void processWebhook_SuccessfulMatch() {
        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayProperties.getApiKey()).thenReturn(null);
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getTolerancePercent()).thenReturn(1.0);
        when(sepayTransactionRepository.existsBySepayId(92704L)).thenReturn(false);

        Payment pendingPayment = new Payment();
        pendingPayment.setPaymentId(UUID.randomUUID());
        pendingPayment.setBooking(booking);
        pendingPayment.setInstallment(inst1);
        pendingPayment.setStatus(PaymentStatus.Pending);
        pendingPayment.setPaymentMethod(PaymentMethod.Sepay);
        pendingPayment.setRequestedAmount(new BigDecimal("300.0000"));
        pendingPayment.setInternalReceiptReference("CASMS BK20260001S1");

        when(paymentRepository.findByStatusAndPaymentMethod(
                        PaymentStatus.Pending, PaymentMethod.Sepay))
                .thenReturn(List.of(pendingPayment));

        SepayTransaction savedTx = new SepayTransaction();
        when(sepayTransactionRepository.save(any(SepayTransaction.class))).thenReturn(savedTx);

        when(paymentService.confirmPayment(
                        eq(pendingPayment.getPaymentId()), any(PaymentConfirmRequest.class)))
                .thenReturn(
                        PaymentResponse.builder()
                                .paymentId(pendingPayment.getPaymentId())
                                .status(PaymentStatus.Confirmed)
                                .build());

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92704L);
        payload.setGateway("MBBank");
        payload.setAccountNumber("0901452366");
        payload.setTransferType("in");
        payload.setTransferAmount(new BigDecimal("7620000"));
        payload.setContent("CASMS BK20260001S1");
        payload.setReferenceCode("MBVCB.3278819588");

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertEquals("Payment confirmed successfully", resp.getMessage());
        assertEquals(pendingPayment.getPaymentId(), resp.getPaymentId());

        ArgumentCaptor<PaymentConfirmRequest> reqCaptor =
                ArgumentCaptor.forClass(PaymentConfirmRequest.class);
        verify(paymentService).confirmPayment(eq(pendingPayment.getPaymentId()), reqCaptor.capture());
        assertEquals("SEPAY-92704", reqCaptor.getValue().getProcessorReference());
    }

    @Test
    @DisplayName("Webhook SePay: Khớp thành công mã booking chuẩn BK-0001 từ nội dung BK0001")
    void processWebhook_StandardCasmsBooking_BK0001_Success() {
        Booking stdBooking = new Booking();
        stdBooking.setBookingId(UUID.randomUUID());
        stdBooking.setBookingReference("BK-0001");
        stdBooking.setBookingStatus(BookingStatus.Draft);
        stdBooking.setCurrency("USD");

        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayProperties.getApiKey()).thenReturn(null);
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getTolerancePercent()).thenReturn(1.0);
        when(sepayTransactionRepository.existsBySepayId(92705L)).thenReturn(false);

        Payment pendingPayment = new Payment();
        pendingPayment.setPaymentId(UUID.randomUUID());
        pendingPayment.setBooking(stdBooking);
        pendingPayment.setStatus(PaymentStatus.Pending);
        pendingPayment.setPaymentMethod(PaymentMethod.Sepay);
        pendingPayment.setRequestedAmount(new BigDecimal("300.0000"));
        pendingPayment.setInternalReceiptReference("CASMS BK0001S1");

        when(paymentRepository.findByStatusAndPaymentMethod(
                        PaymentStatus.Pending, PaymentMethod.Sepay))
                .thenReturn(List.of(pendingPayment));

        when(sepayTransactionRepository.save(any(SepayTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92705L);
        payload.setTransferType("in");
        payload.setTransferAmount(new BigDecimal("7620000"));
        payload.setContent("CASMS BK0001S1");

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertEquals(pendingPayment.getPaymentId(), resp.getPaymentId());
        verify(paymentService).confirmPayment(eq(pendingPayment.getPaymentId()), any());
    }

    @Test
    @DisplayName("Webhook SePay: Khi confirmPayment ném BusinessException, giao dịch vẫn lưu UNMATCHED và trả về HTTP 200 để tránh SePay retry vô hạn")
    void processWebhook_ConfirmException_SavesAuditTrailWithoutRollback() {
        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayProperties.getApiKey()).thenReturn(null);
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getTolerancePercent()).thenReturn(1.0);
        when(sepayTransactionRepository.existsBySepayId(92706L)).thenReturn(false);

        Payment pendingPayment = new Payment();
        pendingPayment.setPaymentId(UUID.randomUUID());
        pendingPayment.setBooking(booking);
        pendingPayment.setStatus(PaymentStatus.Pending);
        pendingPayment.setPaymentMethod(PaymentMethod.Sepay);
        pendingPayment.setRequestedAmount(new BigDecimal("300.0000"));
        pendingPayment.setInternalReceiptReference("CASMS BK20260001S1");

        when(paymentRepository.findByStatusAndPaymentMethod(
                        PaymentStatus.Pending, PaymentMethod.Sepay))
                .thenReturn(List.of(pendingPayment));

        SepayTransaction txRecord = new SepayTransaction();
        when(sepayTransactionRepository.save(any(SepayTransaction.class))).thenReturn(txRecord);

        // Giả lập booking hold đã hết hạn khi SePay gọi webhook
        doThrow(new BusinessException("BOOKING_HOLD_EXPIRED", "Booking hold has expired"))
                .when(paymentService)
                .confirmPayment(eq(pendingPayment.getPaymentId()), any(PaymentConfirmRequest.class));

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92706L);
        payload.setTransferType("in");
        payload.setTransferAmount(new BigDecimal("7620000"));
        payload.setContent("CASMS BK20260001S1");

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getMessage().contains("BOOKING_HOLD_EXPIRED") || resp.getMessage().contains("failed"));

        // Giao dịch SePay phải được lưu lại trạng thái UNMATCHED để admin/kế toán tra soát
        verify(sepayTransactionRepository, atLeastOnce()).save(argThat(tx -> tx.getStatus() == SepayTransactionStatus.UNMATCHED));
    }

    @Test
    @DisplayName(
            "Xử lý Webhook SePay: Trùng mã giao dịch SePay (Idempotent) -> trả về thành công không"
                + " xử lý lại")
    void processWebhook_Idempotent_AlreadyProcessed() {
        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayTransactionRepository.existsBySepayId(92704L)).thenReturn(true);

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92704L);
        payload.setTransferType("in");

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertEquals("Transaction already processed", resp.getMessage());
        verify(paymentService, never()).confirmPayment(any(), any());
    }

    @Test
    @DisplayName("Xử lý Webhook SePay: Khóa API Key không hợp lệ -> ném lỗi 401 Unauthorized")
    void processWebhook_InvalidApiKey_ThrowsUnauthorized() {
        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayProperties.getApiKey()).thenReturn("secret-sepay-token");

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92704L);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> sepayService.processWebhook(payload, "Apikey wrong-key"));
        assertEquals("INVALID_SEPAY_API_KEY", ex.getCode());
    }

    @Test
    @DisplayName("Xử lý Webhook SePay: Bỏ qua giao dịch chuyển tiền đi (transferType == out)")
    void processWebhook_NonIncomingTransfer_Ignored() {
        when(sepayProperties.isEnabled()).thenReturn(true);

        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(92704L);
        payload.setTransferType("out");

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertEquals("Ignored non-incoming transfer", resp.getMessage());
        verify(paymentService, never()).confirmPayment(any(), any());
    }

    @Test
    @DisplayName("Tra cứu trạng thái thanh toán theo paymentId thành công")
    void getPaymentStatus_Success() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setBooking(booking);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setPaymentMethod(PaymentMethod.Sepay);
        payment.setRequestedAmount(new BigDecimal("300.0000"));
        payment.setConfirmedAmount(new BigDecimal("300.0000"));
        payment.setCurrency("USD");
        payment.setRequestedAt(Instant.now().minusSeconds(60));
        payment.setConfirmedAt(Instant.now());
        payment.setProcessorReference("MBVCB.3278819588");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(payment));

        PaymentStatusCheckResponse resp = sepayService.getPaymentStatus(paymentId, null, true);

        assertNotNull(resp);
        assertEquals(paymentId, resp.getPaymentId());
        assertEquals(bookingId, resp.getBookingId());
        assertEquals(PaymentStatus.Confirmed, resp.getStatus());
        assertEquals(new BigDecimal("300.0000"), resp.getConfirmedAmount());
        assertEquals(new BigDecimal("700.0000"), resp.getRemainingBookingBalance());
        assertFalse(resp.isBookingConfirmed(), "A settled payment does not confirm an unsigned contract booking");
    }

    @Test
    @DisplayName(
            "Tạo thanh toán SePay nhanh theo số tiền (Quick Checkout) thành công với trạng thái"
                + " Pending")
    void createQuickCheckout_Success() {
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountName()).thenReturn("CONG TY DU THUYEN CASMS");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(
                        inv -> {
                            Payment p = inv.getArgument(0);
                            p.setPaymentId(UUID.randomUUID());
                            return p;
                        });

        SepayQuickCheckoutRequest req =
                SepayQuickCheckoutRequest.builder()
                        .amountVnd(new BigDecimal("100000"))
                        .transferContent("CASMS BK20264512F")
                        .description("Thanh toán theo số tiền")
                        .build();

        SepayCheckoutResponse resp = sepayService.createQuickCheckout(req);

        assertNotNull(resp);
        assertNotNull(resp.getPaymentId());
        assertEquals(PaymentStatus.Pending, resp.getStatus());
        assertEquals("CASMS BK20264512F", resp.getTransferContent());
        assertEquals("0901452366", resp.getAccountNumber());
        assertEquals("MBBank", resp.getBankName());
        assertTrue(
                resp.getQrCodeUrl().contains("CASMS+BK20264512F")
                        || resp.getQrCodeUrl().contains("CASMS%20BK20264512F"));

        verify(paymentRepository)
                .save(
                        argThat(
                                p ->
                                        p.getStatus() == PaymentStatus.Pending
                                                && "CASMS BK20264512F"
                                                        .equals(p.getInternalReceiptReference())
                                                && p.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && p.getPaymentMethod() == PaymentMethod.Sepay));
    }

    @Test
    @DisplayName("Waterfall: Khi đơn thanh toán 100% (FULL), toàn bộ các đợt trả góp hiển thị đã thanh toán đủ")
    void getPaymentOptions_WaterfallAllocation_FullPaid() {
        Payment fullPayment = new Payment();
        fullPayment.setPaymentId(UUID.randomUUID());
        fullPayment.setBooking(booking);
        fullPayment.setOperationType(PaymentOperationType.Collect);
        fullPayment.setStatus(PaymentStatus.Confirmed);
        fullPayment.setConfirmedAmount(new BigDecimal("1000.0000"));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(fullPayment));
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));

        BookingPaymentOptionsResponse resp = sepayService.getPaymentOptions(bookingId, null, true);

        assertNotNull(resp);
        assertEquals(0, resp.getOutstandingBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, resp.getOutstandingBalanceVnd().compareTo(BigDecimal.ZERO));

        // Deposit option đã hoàn tất
        assertNotNull(resp.getDepositPaymentOption());
        assertTrue(resp.getDepositPaymentOption().isPaid());
        assertFalse(resp.getDepositPaymentOption().isAvailable());
        assertEquals(0, resp.getDepositPaymentOption().getRemainingAmountUsd().compareTo(BigDecimal.ZERO));

        // Cả 2 giai đoạn đều đã thanh toán đủ và không đợt nào là currentDue
        assertNotNull(resp.getStagePaymentOption());
        var stages = resp.getStagePaymentOption().getStages();
        assertEquals(2, stages.size());
        assertTrue(stages.get(0).isPaid());
        assertEquals(0, stages.get(0).getRemainingAmountUsd().compareTo(BigDecimal.ZERO));
        assertFalse(stages.get(0).isCurrentDue());

        assertTrue(stages.get(1).isPaid());
        assertEquals(0, stages.get(1).getRemainingAmountUsd().compareTo(BigDecimal.ZERO));
        assertFalse(stages.get(1).isCurrentDue());
    }

    @Test
    @DisplayName("Waterfall: Khi khách chuyển khoản nhanh hoặc trực tiếp không gắn installment, tiền tự động bù đủ Đợt 1")
    void getPaymentOptions_WaterfallAllocation_PartialUntaggedPayment() {
        Payment partialPayment = new Payment();
        partialPayment.setPaymentId(UUID.randomUUID());
        partialPayment.setBooking(booking);
        partialPayment.setInstallment(null); // Không gắn installment cụ thể
        partialPayment.setOperationType(PaymentOperationType.Collect);
        partialPayment.setStatus(PaymentStatus.Confirmed);
        partialPayment.setConfirmedAmount(new BigDecimal("300.0000"));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(partialPayment));
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));

        BookingPaymentOptionsResponse resp = sepayService.getPaymentOptions(bookingId, null, true);

        assertNotNull(resp);
        assertEquals(0, resp.getOutstandingBalance().compareTo(new BigDecimal("700.0000")));

        // Đợt 1 (300 USD) được nhận trọn vẹn 300 USD từ khoản thanh toán
        var stages = resp.getStagePaymentOption().getStages();
        assertTrue(stages.get(0).isPaid());
        assertEquals(0, stages.get(0).getPaidAmountUsd().compareTo(new BigDecimal("300.0000")));
        assertEquals(0, stages.get(0).getRemainingAmountUsd().compareTo(BigDecimal.ZERO));
        assertFalse(stages.get(0).isCurrentDue());

        // Đợt 2 (700 USD) còn nợ và trở thành currentDue
        assertFalse(stages.get(1).isPaid());
        assertEquals(0, stages.get(1).getPaidAmountUsd().compareTo(BigDecimal.ZERO));
        assertEquals(0, stages.get(1).getRemainingAmountUsd().compareTo(new BigDecimal("700.0000")));
        assertTrue(stages.get(1).isCurrentDue());
    }

    @Test
    @DisplayName("Webhook: Khách chuyển khoản thừa tiền (gộp đợt 1 + đợt 2), hệ thống tự động sinh Payment đợt kế tiếp")
    void processWebhook_SurplusPayment_AllocatesToSubsequentStage() {
        when(sepayProperties.isEnabled()).thenReturn(true);
        when(sepayProperties.getApiKey()).thenReturn(null);
        when(sepayProperties.getUsdToVndRate()).thenReturn(new BigDecimal("25400"));

        Payment pendingPayment1 = new Payment();
        pendingPayment1.setPaymentId(UUID.randomUUID());
        pendingPayment1.setBooking(booking);
        pendingPayment1.setInstallment(inst1);
        pendingPayment1.setOperationType(PaymentOperationType.Collect);
        pendingPayment1.setPaymentMethod(PaymentMethod.Sepay);
        pendingPayment1.setCurrency("USD");
        pendingPayment1.setRequestedAmount(new BigDecimal("300.0000"));
        pendingPayment1.setRequestedAt(Instant.now());
        pendingPayment1.setStatus(PaymentStatus.Pending);
        pendingPayment1.setInternalReceiptReference("CASMS BK0001S1");
        pendingPayment1.setIdempotencyKey("SEPAY-BK0001-S1");

        when(paymentRepository.findByStatusAndPaymentMethod(PaymentStatus.Pending, PaymentMethod.Sepay))
                .thenReturn(List.of(pendingPayment1));

        // Khách chuyển 25,400,000 VND (~1,000 USD, thừa 700 USD so với yêu cầu 300 USD của đợt 1)
        SepayWebhookPayload payload = new SepayWebhookPayload();
        payload.setId(99998888L);
        payload.setTransferType("in");
        payload.setTransferAmount(new BigDecimal("25400000"));
        payload.setContent("CASMS BK0001S1 THANH TOAN GOP");
        payload.setReferenceCode("REF-BANK-9999");

        when(sepayTransactionRepository.existsBySepayId(99998888L)).thenReturn(false);
        when(sepayTransactionRepository.save(any(SepayTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.confirmPayment(any(), any())).thenAnswer(inv -> {
            pendingPayment1.setStatus(PaymentStatus.Confirmed);
            pendingPayment1.setConfirmedAmount(pendingPayment1.getRequestedAmount());
            return null;
        });
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of(inst1, inst2));
        when(paymentRepository.findForBooking(bookingId)).thenReturn(List.of(pendingPayment1));

        SepayWebhookResponse resp = sepayService.processWebhook(payload, null);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getMessage().contains("surplus automatically allocated"));

        // Kiểm tra đã lưu bản ghi surplus payment vào paymentRepository
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
        Payment savedSurplus = paymentCaptor.getValue();
        assertNotNull(savedSurplus);
        assertEquals(0, savedSurplus.getConfirmedAmount().compareTo(new BigDecimal("700.0000")));
        assertEquals(inst2, savedSurplus.getInstallment());
        assertEquals(PaymentStatus.Confirmed, savedSurplus.getStatus());

        // Kiểm tra đã đồng bộ lại installments
        verify(paymentService, times(1)).allocateInstallmentsAndSync(booking);
    }
}
