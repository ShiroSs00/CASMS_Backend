package com.casms.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.service.BookingService;
import com.casms.identity.entity.User;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.entity.Installment;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.InstallmentType;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.service.SepayService;
import com.casms.ship.entity.Cruise;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaymentSchedulerTest {

    @Mock private BookingService bookingService;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private BookingPassengerRepository bookingPassengerRepository;
    @Mock private SepayService sepayService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private PaymentScheduler paymentScheduler;

    private Booking booking;
    private User user;
    private Cruise cruise;
    private Installment instUpcoming;
    private Installment instOverdue;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername("nguyenvanan");
        user.setEmail("an@example.com");

        cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setCruiseName("Hải trình Vịnh Hạ Long 3N2Đ");
        cruise.setCruiseCode("HL-01");

        booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setBookingReference("BK-0001");
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setBookedByUser(user);
        booking.setCruise(cruise);
        booking.setAgreedFareAmount(new BigDecimal("1000.0000"));
        booking.setCurrency("USD");

        instUpcoming = new Installment();
        instUpcoming.setInstallmentId(UUID.randomUUID());
        instUpcoming.setBooking(booking);
        instUpcoming.setSequenceNo(2);
        instUpcoming.setInstallmentType(InstallmentType.FinalFare);
        instUpcoming.setRequiredAmount(new BigDecimal("700.0000"));
        instUpcoming.setCurrency("USD");
        instUpcoming.setDueAt(Instant.now().plus(Duration.ofDays(2)));
        instUpcoming.setStatus(InstallmentStatus.Scheduled);

        instOverdue = new Installment();
        instOverdue.setInstallmentId(UUID.randomUUID());
        instOverdue.setBooking(booking);
        instOverdue.setSequenceNo(2);
        instOverdue.setInstallmentType(InstallmentType.FinalFare);
        instOverdue.setRequiredAmount(new BigDecimal("700.0000"));
        instOverdue.setCurrency("USD");
        instOverdue.setDueAt(Instant.now().minus(Duration.ofDays(1)));
        instOverdue.setStatus(InstallmentStatus.Scheduled);
    }

    @Test
    @DisplayName("runScan: Giải phóng đơn Draft quá hạn giữ chỗ")
    void runScan_ReleasesExpiredHolds() {
        when(bookingService.releaseExpiredHolds()).thenReturn(3);
        when(installmentRepository.findByStatusAndDueAtBetween(any(), any(), any())).thenReturn(List.of());
        when(installmentRepository.findByStatusAndDueAtBefore(any(), any())).thenReturn(List.of());

        PaymentScheduler.PaymentScheduleScanResult result = paymentScheduler.runScan();

        assertNotNull(result);
        assertEquals(3, result.getReleasedHoldsCount());
        verify(bookingService, times(1)).releaseExpiredHolds();
    }

    @Test
    @DisplayName("runScan: Quét đợt sắp đến hạn trong 3 ngày, ghi Email Outbox và tạo Notification")
    void runScan_UpcomingInstallmentReminder() {
        when(bookingService.releaseExpiredHolds()).thenReturn(0);
        when(installmentRepository.findByStatusAndDueAtBetween(eq(InstallmentStatus.Scheduled), any(), any()))
                .thenReturn(List.of(instUpcoming));
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId()))
                .thenReturn(List.of(instUpcoming));
        when(sepayService.calculateWaterfallInstallmentPaidAmounts(eq(booking.getBookingId()), any()))
                .thenReturn(Map.of(instUpcoming.getInstallmentId(), BigDecimal.ZERO)); // Chưa trả đồng nào

        // Giả lập ghi thành công vào BookingEmailOutbox (1 dòng được thêm)
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(installmentRepository.findByStatusAndDueAtBefore(any(), any())).thenReturn(List.of());

        PaymentScheduler.PaymentScheduleScanResult result = paymentScheduler.runScan();

        assertNotNull(result);
        assertEquals(1, result.getUpcomingRemindersCount());

        // Kiểm tra đã insert vào BookingEmailOutbox
        verify(jdbcTemplate, times(1)).update(
                contains("BookingEmailOutbox"),
                contains("PAYMENT_REMINDER"),
                eq(booking.getBookingId()),
                eq("an@example.com"),
                contains("Nhắc nhở hạn thanh toán Đợt 2"),
                contains("700.0000 USD"));

        // Kiểm tra đã lưu Notification
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("runScan: Quét đợt đã quá hạn cho đơn Confirmed, ghi Email Outbox cảnh báo khẩn cấp")
    void runScan_OverdueInstallmentAlert() {
        when(bookingService.releaseExpiredHolds()).thenReturn(0);
        when(installmentRepository.findByStatusAndDueAtBetween(any(), any(), any())).thenReturn(List.of());
        when(installmentRepository.findByStatusAndDueAtBefore(eq(InstallmentStatus.Scheduled), any()))
                .thenReturn(List.of(instOverdue));
        when(installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId()))
                .thenReturn(List.of(instOverdue));
        when(sepayService.calculateWaterfallInstallmentPaidAmounts(eq(booking.getBookingId()), any()))
                .thenReturn(Map.of(instOverdue.getInstallmentId(), BigDecimal.ZERO)); // Chưa trả

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        PaymentScheduler.PaymentScheduleScanResult result = paymentScheduler.runScan();

        assertNotNull(result);
        assertEquals(1, result.getOverdueAlertsCount());

        verify(jdbcTemplate, times(1)).update(
                contains("BookingEmailOutbox"),
                contains("PAYMENT_OVERDUE"),
                eq(booking.getBookingId()),
                eq("an@example.com"),
                contains("CẢNH BÁO"),
                contains("ĐÃ QUÁ HẠN"));

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }
}

