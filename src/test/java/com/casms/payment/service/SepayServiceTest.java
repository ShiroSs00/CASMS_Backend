package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
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
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.SepayTransactionRepository;
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

@ExtendWith(MockitoExtension.class)
class SepayServiceTest {

    @Mock
    private SepayProperties sepayProperties;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private SepayTransactionRepository sepayTransactionRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private SepayService sepayService;

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
        assertEquals(new BigDecimal("300.0000"), resp.getDepositPaymentOption().getRemainingAmountUsd());
        assertEquals(new BigDecimal("7620000"), resp.getDepositPaymentOption().getRemainingAmountVnd());

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

        BusinessException ex = assertThrows(BusinessException.class, () -> sepayService.createCheckout(req, null, true));
        assertEquals("DEPOSIT_ALREADY_PAID", ex.getCode());
    }

    @Test
    @DisplayName("Xử lý Webhook SePay thành công: khớp payment, gọi confirmPayment và cập nhật PROCESSED")
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

        when(paymentRepository.findAll()).thenReturn(List.of(pendingPayment));

        SepayTransaction savedTx = new SepayTransaction();
        when(sepayTransactionRepository.save(any(SepayTransaction.class))).thenReturn(savedTx);

        when(paymentService.confirmPayment(eq(pendingPayment.getPaymentId()), any(PaymentConfirmRequest.class)))
                .thenReturn(PaymentResponse.builder().paymentId(pendingPayment.getPaymentId()).status(PaymentStatus.Confirmed).build());

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
        verify(paymentService).confirmPayment(eq(pendingPayment.getPaymentId()), any(PaymentConfirmRequest.class));
    }

    @Test
    @DisplayName("Xử lý Webhook SePay: Trùng mã giao dịch SePay (Idempotent) -> trả về thành công không xử lý lại")
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

        BusinessException ex = assertThrows(BusinessException.class,
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
    }
}
