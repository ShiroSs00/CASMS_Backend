package com.casms.testng;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.service.PaymentService;
import com.casms.testng.listener.CasmsTestListener;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.testng.Assert;
import org.testng.annotations.*;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Listeners(CasmsTestListener.class)
public class PaymentServiceTestNG {

    private AutoCloseable mocks;

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private InstallmentRepository installmentRepository;

    @InjectMocks private PaymentService paymentService;

    private UUID bookingId;
    private UUID paymentId;
    private Booking booking;
    private Payment payment;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        paymentService = null;
        mocks = MockitoAnnotations.openMocks(this);
        bookingId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingReference("BK-PAY-001");
        booking.setBookingStatus(BookingStatus.Draft);
        booking.setAgreedFareAmount(new BigDecimal("10000000"));
        booking.setRequiredDepositAmount(new BigDecimal("3000000"));
        booking.setCurrency("USD");

        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setBooking(booking);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.GatewaySandbox);
        payment.setRequestedAmount(new BigDecimal("10000000"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey("IDEMP-NG-001");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test(groups = {"smoke", "regression"})
    public void testCreatePaymentWithNewIdempotencyKeySucceeds() {
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of());
        when(paymentRepository.findByIdempotencyKey("IDEMP-NG-001")).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("10000000"));
        req.setPaymentMethod(PaymentMethod.GatewaySandbox);
        req.setIdempotencyKey("IDEMP-NG-001");

        PaymentResponse resp = paymentService.createPayment(req);

        SoftAssert soft = new SoftAssert();
        soft.assertNotNull(resp);
        soft.assertEquals(resp.getStatus(), PaymentStatus.Pending);
        soft.assertEquals(resp.getIdempotencyKey(), "IDEMP-NG-001");
        soft.assertAll();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test(groups = {"smoke", "regression"})
    public void testCreatePaymentDuplicateIdempotencyKeyReturnsExisting() {
        when(paymentRepository.findByIdempotencyKey("IDEMP-NG-001")).thenReturn(Optional.of(payment));

        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("10000000"));
        req.setPaymentMethod(PaymentMethod.GatewaySandbox);
        req.setIdempotencyKey("IDEMP-NG-001");

        PaymentResponse resp = paymentService.createPayment(req);

        Assert.assertEquals(resp.getPaymentId(), paymentId);
        verify(paymentRepository, never()).save(any());
    }

    @DataProvider(name = "mismatchedCurrencies")
    public Object[][] mismatchedCurrencies() {
        return new Object[][] {
            {"EUR"},
            {"JPY"},
            {"SGD"},
            {"GBP"},
        };
    }

    @Test(dataProvider = "mismatchedCurrencies", groups = {"regression", "boundary"})
    public void testCreatePaymentRejectsCurrencyMismatch(String wrongCurrency) {
        when(paymentRepository.findByIdempotencyKey("KEY-" + wrongCurrency))
                .thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        PaymentCreateRequest req = new PaymentCreateRequest();
        req.setBookingId(bookingId);
        req.setRequestedAmount(new BigDecimal("100"));
        req.setPaymentMethod(PaymentMethod.Cash);
        req.setIdempotencyKey("KEY-" + wrongCurrency);
        req.setCurrency(wrongCurrency);

        BusinessException ex =
                Assert.expectThrows(BusinessException.class, () -> paymentService.createPayment(req));
        Assert.assertEquals(ex.getCode(), "PAYMENT_CURRENCY_MISMATCH");
        verify(paymentRepository, never()).save(any());
    }

    @Test(groups = {"regression"})
    public void testConfirmPaymentUpdatesTransactionWithoutAutoConfirmingBooking() {
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId))
                .thenReturn(List.of());
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        Payment confirmed = new Payment();
        confirmed.setPaymentId(paymentId);
        confirmed.setBooking(booking);
        confirmed.setStatus(PaymentStatus.Confirmed);
        confirmed.setConfirmedAmount(new BigDecimal("10000000"));
        confirmed.setConfirmedAt(Instant.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(confirmed);

        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setConfirmedAmount(new BigDecimal("10000000"));
        req.setProcessorReference("SEPAY-REF-9001");

        PaymentResponse resp = paymentService.confirmPayment(paymentId, req);

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(resp.getStatus(), PaymentStatus.Confirmed);
        soft.assertEquals(booking.getBookingStatus(), BookingStatus.Draft);
        soft.assertEquals(payment.getProcessorReference(), "SEPAY-REF-9001");
        soft.assertAll();
        verify(bookingRepository, never()).save(any());
    }

    @Test(groups = {"regression", "boundary"})
    public void testConfirmPaymentRejectsAmountAboveRequested() {
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setConfirmedAmount(new BigDecimal("10000001"));
        req.setProcessorReference("TXN-OVER");

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> paymentService.confirmPayment(paymentId, req));
        Assert.assertEquals(ex.getCode(), "INVALID_CONFIRMED_AMOUNT");
    }

    @Test(groups = {"regression", "security"}, expectedExceptions = AccessDeniedException.class)
    public void testPassengerCannotReadAnotherUsersPayments() {
        User owner = new User();
        owner.setUserId(UUID.randomUUID());
        booking.setBookedByUser(owner);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        paymentService.getPaymentsForBooking(bookingId, UUID.randomUUID(), false);
    }
}
