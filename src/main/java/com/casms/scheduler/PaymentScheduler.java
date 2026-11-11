package com.casms.scheduler;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.service.BookingService;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.entity.Installment;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.service.SepayService;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Cron Job định kỳ quét và xử lý nghiệp vụ thanh toán:
 * 1. Tự động giải phóng các đơn đặt chỗ tạm (Draft/Hold) đã hết hạn giữ chỗ (releaseExpiredHolds).
 * 2. Gửi Email Outbox & Notification nhắc nhở các đợt thanh toán sắp đến hạn (trong vòng 3 ngày tới).
 * 3. Gửi Email Outbox & Notification cảnh báo khẩn cấp các đợt thanh toán đã quá hạn cho đơn đã Confirmed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "casms.payment.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentScheduler {

    private final BookingService bookingService;
    private final InstallmentRepository installmentRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final SepayService sepayService;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    @Getter
    @Builder
    public static class PaymentScheduleScanResult {
        private int releasedHoldsCount;
        private int upcomingRemindersCount;
        private int overdueAlertsCount;
    }

    /**
     * Chạy định kỳ mỗi giờ để đối soát toàn bộ đợt thanh toán và đơn tạm.
     * Mặc định cron: "0 0 * * * *" (Đầu mỗi giờ). Có thể tùy chỉnh trong application.properties.
     */
    @Scheduled(cron = "${casms.payment.scheduler.cron:0 0 * * * *}")
    @Transactional
    public void execute() {
        log.info("Running scheduled payment tasks: scanning expired holds, upcoming reminders, and overdue alerts");
        try {
            PaymentScheduleScanResult result = runScan();
            log.info(
                    "Payment scheduler completed: released {} holds, queued {} reminders, queued {} overdue alerts",
                    result.getReleasedHoldsCount(),
                    result.getUpcomingRemindersCount(),
                    result.getOverdueAlertsCount());
        } catch (Exception e) {
            log.error("Error during payment scheduler execution: {}", e.getMessage(), e);
        }
    }

    /**
     * Thực thi quét và xử lý (có thể gọi trực tiếp từ Service hoặc Unit Test).
     */
    @Transactional
    public PaymentScheduleScanResult runScan() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // 1. Tự động hủy đơn Draft quá hạn giữ chỗ & nhả phòng
        int releasedHolds = bookingService.releaseExpiredHolds();

        // 2. Quét các đợt sắp đến hạn trong vòng 3 ngày tới (Upcoming Reminders)
        int remindersQueued = scanUpcomingInstallmentReminders(now, today);

        // 3. Quét các đợt đã quá hạn đối với các đơn đã xác nhận (Overdue Alerts)
        int overdueQueued = scanOverdueInstallmentAlerts(now, today);

        return PaymentScheduleScanResult.builder()
                .releasedHoldsCount(releasedHolds)
                .upcomingRemindersCount(remindersQueued)
                .overdueAlertsCount(overdueQueued)
                .build();
    }

    /**
     * Nhắc nhở đợt thanh toán sắp đến hạn trong 72 giờ tới (Chống spam: tối đa 1 email/ngày/đợt).
     */
    private int scanUpcomingInstallmentReminders(Instant now, LocalDate today) {
        Instant threeDaysLater = now.plus(Duration.ofDays(3));
        List<Installment> upcomingInstallments =
                installmentRepository.findByStatusAndDueAtBetween(
                        InstallmentStatus.Scheduled, now, threeDaysLater);

        int count = 0;
        Map<UUID, Map<UUID, BigDecimal>> bookingPaidCache = new HashMap<>();

        for (Installment inst : upcomingInstallments) {
            Booking booking = inst.getBooking();
            if (booking == null || booking.getBookingStatus() == BookingStatus.Cancelled) {
                continue;
            }

            Map<UUID, BigDecimal> paidMap =
                    bookingPaidCache.computeIfAbsent(
                            booking.getBookingId(),
                            bId -> {
                                List<Installment> allInst =
                                        installmentRepository
                                                .findByBookingBookingIdOrderBySequenceNoAsc(bId);
                                return sepayService.calculateWaterfallInstallmentPaidAmounts(
                                        bId, allInst);
                            });

            BigDecimal paid = paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remaining = inst.getRequiredAmount().subtract(paid);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Đợt này đã thanh toán xong
            }

            String eventKey =
                    "PAYMENT_REMINDER:"
                            + booking.getBookingId()
                            + ":S"
                            + inst.getSequenceNo()
                            + ":"
                            + today;

            RecipientInfo recipient = resolveRecipient(booking);
            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                continue;
            }

            String cruiseName =
                    booking.getCruise() != null ? booking.getCruise().getCruiseName() : "Du thuyền CASMS";
            String dueDateStr = DATE_FORMATTER.format(inst.getDueAt());
            String subject =
                    "[CASMS] Nhắc nhở hạn thanh toán Đợt "
                            + inst.getSequenceNo()
                            + " - Mã đặt tour: "
                            + booking.getBookingReference();

            String body =
                    "Kính gửi "
                            + recipient.getName()
                            + ",\n\n"
                            + "Hệ thống CASMS xin thông báo đợt thanh toán số "
                            + inst.getSequenceNo()
                            + " ("
                            + inst.getInstallmentType()
                            + ") cho chuyến hải trình "
                            + cruiseName
                            + " (Mã đơn: "
                            + booking.getBookingReference()
                            + ") sẽ đến hạn vào lúc "
                            + dueDateStr
                            + " UTC.\n\n"
                            + "• Số tiền cần thanh toán: "
                            + remaining
                            + " "
                            + inst.getCurrency()
                            + "\n"
                            + "• Hạn chót: "
                            + dueDateStr
                            + " UTC\n\n"
                            + "Quý khách vui lòng mở ứng dụng ngân hàng quét mã SePay VietQR hoặc hoàn tất chuyển khoản đúng hạn để đảm bảo quyền lợi hành trình.\n\n"
                            + "Trân trọng,\n"
                            + "Ban Quản Lý Du Thuyền CASMS";

            boolean enqueued = enqueueEmailOutbox(eventKey, booking.getBookingId(), recipient.getEmail(), subject, body);
            if (enqueued) {
                count++;
                if (booking.getBookedByUser() != null) {
                    Notification notification = new Notification();
                    notification.setRecipientUser(booking.getBookedByUser());
                    notification.setNotificationType("PAYMENT_REMINDER");
                    notification.setTitle("Nhắc nhở hạn thanh toán Đợt " + inst.getSequenceNo());
                    notification.setContent(
                            "Đợt thanh toán số "
                                    + inst.getSequenceNo()
                                    + " ("
                                    + remaining
                                    + " "
                                    + inst.getCurrency()
                                    + ") cho mã đơn "
                                    + booking.getBookingReference()
                                    + " sẽ đến hạn vào "
                                    + dueDateStr
                                    + ". Vui lòng hoàn tất thanh toán.");
                    notificationRepository.save(notification);
                }
            }
        }
        return count;
    }

    /**
     * Cảnh báo khẩn cấp các đợt thanh toán đã quá hạn cho các đơn Confirmed (Tối đa 1 thông báo/ngày/đợt).
     */
    private int scanOverdueInstallmentAlerts(Instant now, LocalDate today) {
        List<Installment> overdueInstallments =
                installmentRepository.findByStatusAndDueAtBefore(
                        InstallmentStatus.Scheduled, now);

        int count = 0;
        Map<UUID, Map<UUID, BigDecimal>> bookingPaidCache = new HashMap<>();

        for (Installment inst : overdueInstallments) {
            Booking booking = inst.getBooking();
            if (booking == null || booking.getBookingStatus() != BookingStatus.Confirmed) {
                continue;
            }

            Map<UUID, BigDecimal> paidMap =
                    bookingPaidCache.computeIfAbsent(
                            booking.getBookingId(),
                            bId -> {
                                List<Installment> allInst =
                                        installmentRepository
                                                .findByBookingBookingIdOrderBySequenceNoAsc(bId);
                                return sepayService.calculateWaterfallInstallmentPaidAmounts(
                                        bId, allInst);
                            });

            BigDecimal paid = paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remaining = inst.getRequiredAmount().subtract(paid);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Đã thanh toán đầy đủ
            }

            String eventKey =
                    "PAYMENT_OVERDUE:"
                            + booking.getBookingId()
                            + ":S"
                            + inst.getSequenceNo()
                            + ":"
                            + today;

            RecipientInfo recipient = resolveRecipient(booking);
            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                continue;
            }

            String cruiseName =
                    booking.getCruise() != null ? booking.getCruise().getCruiseName() : "Du thuyền CASMS";
            String dueDateStr = DATE_FORMATTER.format(inst.getDueAt());
            String subject =
                    "[CASMS CẢNH BÁO] Đợt thanh toán số "
                            + inst.getSequenceNo()
                            + " ĐÃ QUÁ HẠN - Mã đặt tour: "
                            + booking.getBookingReference();

            String body =
                    "Kính gửi "
                            + recipient.getName()
                            + ",\n\n"
                            + "Hệ thống CASMS ghi nhận đợt thanh toán số "
                            + inst.getSequenceNo()
                            + " ("
                            + inst.getInstallmentType()
                            + ") cho đơn đặt tour "
                            + booking.getBookingReference()
                            + " (Hải trình: "
                            + cruiseName
                            + ") ĐÃ QUÁ HẠN từ ngày "
                            + dueDateStr
                            + " UTC.\n\n"
                            + "• Số tiền còn nợ: "
                            + remaining
                            + " "
                            + inst.getCurrency()
                            + "\n"
                            + "• Hạn ban đầu: "
                            + dueDateStr
                            + " UTC\n\n"
                            + "Quý khách vui lòng thanh toán ngay qua cổng SePay hoặc liên hệ tổng đài hỗ trợ để tránh việc đơn đặt chỗ bị xử lý hủy hoặc áp dụng chế tài theo hợp đồng du thuyền.\n\n"
                            + "Trân trọng,\n"
                            + "Ban Quản Lý Du Thuyền CASMS";

            boolean enqueued = enqueueEmailOutbox(eventKey, booking.getBookingId(), recipient.getEmail(), subject, body);
            if (enqueued) {
                count++;
                if (booking.getBookedByUser() != null) {
                    Notification notification = new Notification();
                    notification.setRecipientUser(booking.getBookedByUser());
                    notification.setNotificationType("PAYMENT_OVERDUE");
                    notification.setTitle("CẢNH BÁO QUÁ HẠN: Đợt thanh toán số " + inst.getSequenceNo());
                    notification.setContent(
                            "Đợt thanh toán số "
                                    + inst.getSequenceNo()
                                    + " ("
                                    + remaining
                                    + " "
                                    + inst.getCurrency()
                                    + ") cho mã đơn "
                                    + booking.getBookingReference()
                                    + " đã quá hạn từ "
                                    + dueDateStr
                                    + ". Vui lòng thanh toán ngay.");
                    notificationRepository.save(notification);
                }
            }
        }
        return count;
    }

    /**
     * Ghi bản ghi vào bảng "BookingEmailOutbox" với cơ chế Idempotent (ON CONFLICT DO NOTHING).
     * Trả về true nếu là bản ghi mới được thêm, false nếu sự kiện đã tồn tại trước đó.
     */
    private boolean enqueueEmailOutbox(
            String eventKey, UUID bookingId, String recipient, String subject, String body) {
        String sql =
                "INSERT INTO \"BookingEmailOutbox\" (event_key, booking_id, recipient, subject, body, attempts, next_attempt_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, now(), now()) ON CONFLICT (event_key) DO NOTHING";
        int rows = jdbcTemplate.update(sql, eventKey, bookingId, recipient, subject, body);
        return rows > 0;
    }

    /**
     * Tìm thông tin người nhận email đại diện từ User đặt chỗ hoặc danh sách hành khách.
     */
    private RecipientInfo resolveRecipient(Booking booking) {
        if (booking.getBookedByUser() != null
                && booking.getBookedByUser().getEmail() != null
                && !booking.getBookedByUser().getEmail().isBlank()) {
            String name =
                    booking.getBookedByUser().getUsername() != null
                            ? booking.getBookedByUser().getUsername()
                            : "Quý khách";
            return new RecipientInfo(booking.getBookedByUser().getEmail().trim(), name);
        }

        List<BookingPassenger> passengers =
                bookingPassengerRepository.findByBookingBookingId(booking.getBookingId());
        for (BookingPassenger bp : passengers) {
            if (bp.getPassenger() != null
                    && bp.getPassenger().getEmail() != null
                    && !bp.getPassenger().getEmail().isBlank()) {
                String name =
                        bp.getPassenger().getFullName() != null
                                ? bp.getPassenger().getFullName()
                                : "Quý khách";
                return new RecipientInfo(bp.getPassenger().getEmail().trim(), name);
            }
        }

        return new RecipientInfo(null, "Quý khách");
    }

    @Getter
    @RequiredArgsConstructor
    private static class RecipientInfo {
        private final String email;
        private final String name;
    }
}
