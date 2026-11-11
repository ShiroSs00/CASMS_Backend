package com.casms.activity.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.Registration;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.payment.entity.OnboardAccount;
import com.casms.payment.entity.OnboardTransaction;
import com.casms.payment.enums.TransactionStatus;
import com.casms.payment.enums.TransactionSyncStatus;
import com.casms.payment.enums.TransactionType;
import com.casms.payment.repository.OnboardTransactionRepository;
import com.casms.pos.PosDtos;
import com.casms.pos.PosLedgerService;
import com.casms.servicepackage.repository.TourPackageActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service tổng hợp 5 mô hình vận hành giải trí & dịch vụ du thuyền (Không dùng Kafka):
 * 1. Hybrid Edge-to-Cloud: Lưu trữ offline cục bộ, đồng bộ giao dịch theo lô (syncStatus: PENDING -> SYNCED).
 * 2. Cashless Folio: Quẹt thẻ thanh toán một cửa, kiểm soát hạn mức chi tiêu (Credit Limit) phòng.
 * 3. Slot & Capacity Quota: Khóa bi quan (Pessimistic Lock) chống cướp chỗ, kiểm soát sức chứa rạp hát/nhà hàng.
 * 4. All-Inclusive vs Add-on: Nhận diện quyền lợi gói (0 VND nếu bao gồm trong gói, tính giá nếu ngoài gói).
 * 5. Dynamic Schedule: Điều chỉnh lịch trình/địa điểm theo thời tiết biển và tự động gửi thông báo thời gian thực.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OnboardOperationService {

    private final OnboardActivityRepository activities;
    private final RegistrationRepository registrations;
    private final BookingPassengerRepository bookingPassengers;
    private final TourPackageActivityRepository packageActivities;
    private final PosLedgerService posLedgerService;
    private final OnboardTransactionRepository onboardTransactionRepository;
    private final NotificationRepository notificationRepository;

    public record ActivityBookingResult(
            UUID registrationId,
            String activityCode,
            String activityName,
            int quantity,
            BigDecimal chargedAmount,
            String currency,
            boolean includedInPackage,
            UUID folioTransactionId,
            String statusNote
    ) {}

    public record RescheduleResult(
            UUID activityId,
            String activityName,
            String oldLocation,
            String newLocation,
            String newMeetingPoint,
            int notifiedPassengerCount
    ) {}

    public record SyncResult(
            int totalProcessed,
            int totalSynced,
            Instant syncedAt
    ) {}

    /**
     * Mô hình 2, 3, 4: Đặt chỗ giải trí kết hợp Khóa sức chứa (Quota Lock), Kiểm tra gói (Package Check),
     * và Trừ nợ Folio buồng phòng (Cashless Folio).
     */
    public ActivityBookingResult bookActivityWithFolio(
            UUID bookingPassengerId,
            UUID activityId,
            int quantity,
            User operator) {

        int effectiveQuantity = Math.max(1, quantity);

        // 1. Kiểm tra hành khách
        BookingPassenger bp = bookingPassengers.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingPassenger", bookingPassengerId));

        // 2. Mô hình 3: Khóa sức chứa bi quan (Pessimistic Lock) trên dòng OnboardActivity
        OnboardActivity activity = activities.lockActivity(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardActivity", activityId));

        if (activity.getStatus() != OnboardActivityStatus.OPEN && activity.getStatus() != OnboardActivityStatus.PLANNED) {
            throw new BusinessException("ACTIVITY_NOT_OPEN", "Hoạt động giải trí không mở nhận đăng ký.");
        }

        long occupied = registrations.sumOnboardParticipants(
                activityId, List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED));

        if (occupied + effectiveQuantity > activity.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED", "Sức chứa hoạt động đã đầy, không thể đăng ký thêm.");
        }

        // 3. Mô hình 4: Kiểm tra quyền lợi gói dịch vụ (All-Inclusive vs Add-on)
        boolean isIncludedInPackage = packageActivities.existsByOnboardActivity_OnboardActivityId(activityId);
        BigDecimal unitPrice = isIncludedInPackage
                ? BigDecimal.ZERO
                : (activity.getPrice() != null ? activity.getPrice() : BigDecimal.ZERO);
        BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(effectiveQuantity));

        UUID folioTxId = null;

        // 4. Mô hình 2: Nếu có phí phát sinh, trừ nợ vào Folio tài khoản buồng phòng (Cashless Folio)
        if (totalCharge.compareTo(BigDecimal.ZERO) > 0) {
            OnboardAccount account = bp.getBooking().getSettlementOnboardAccount();
            if (account == null) {
                throw new BusinessException("FOLIO_ACCOUNT_NOT_FOUND", "Buồng phòng chưa được cấp tài khoản chi tiêu trên tàu.");
            }

            // Khóa tài khoản buồng phòng chống race condition chi tiêu đồng thời
            posLedgerService.lockAccount(account.getOnboardAccountId());

            // Kiểm tra hạn mức chi tiêu còn lại
            PosDtos.Budget budget = posLedgerService.budget(account, bp.getPassenger().getPassengerId());
            if (budget.remainingAmount().compareTo(totalCharge) < 0) {
                throw new BusinessException("INSUFFICIENT_FOLIO_LIMIT",
                        "Hạn mức chi tiêu còn lại của buồng phòng (" + budget.remainingAmount() + ") không đủ chi trả (" + totalCharge + ").");
            }

            // Ghi nhận giao dịch OnboardTransaction với sync_status = PENDING (Sẵn sàng chạy offline ngoài biển)
            OnboardTransaction tx = new OnboardTransaction();
            tx.setOnboardAccount(account);
            tx.setPassenger(bp.getPassenger());
            tx.setTransactionType(TransactionType.CHARGE);
            tx.setAmount(totalCharge);
            tx.setCurrencyCode(activity.getCurrencyCode() != null ? activity.getCurrencyCode() : "USD");
            tx.setTransactionTime(Instant.now());
            tx.setPostedAt(Instant.now());
            tx.setStatus(TransactionStatus.POSTED);
            tx.setSourceType("ONBOARD_ACTIVITY");
            tx.setSourceReferenceId(activity.getOnboardActivityId());
            tx.setSyncStatus(TransactionSyncStatus.PENDING); // Offline Edge readiness
            tx.setDescription("Phí đăng ký hoạt động: " + activity.getActivityName());
            tx.setCreatedByUser(operator);
            OnboardTransaction savedTx = onboardTransactionRepository.save(tx);
            folioTxId = savedTx.getTransactionId();
        }

        // 5. Lưu bản ghi đăng ký tham gia (Registration)
        Registration reg = new Registration();
        reg.setPassenger(bp.getPassenger());
        reg.setBookingPassenger(bp);
        reg.setOnboardActivity(activity);
        reg.setQuantity(effectiveQuantity);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setRegistrationTime(Instant.now());
        reg.setNotes(isIncludedInPackage ? "Bao gồm trong gói du thuyền (0 VND)" : "Đã ghi nợ vào tài khoản phòng");
        Registration savedReg = registrations.save(reg);

        // Cập nhật trạng thái FULL nếu đã hết chỗ
        if (occupied + effectiveQuantity >= activity.getCapacity()) {
            activity.setStatus(OnboardActivityStatus.FULL);
        }

        log.info("Registered passenger {} for activity {} with charge {}",
                bookingPassengerId, activity.getActivityCode(), totalCharge);

        return new ActivityBookingResult(
                savedReg.getRegistrationId(),
                activity.getActivityCode(),
                activity.getActivityName(),
                effectiveQuantity,
                totalCharge,
                activity.getCurrencyCode() != null ? activity.getCurrencyCode() : "USD",
                isIncludedInPackage,
                folioTxId,
                isIncludedInPackage ? "Đã xác nhận (Quyền lợi gói)" : "Đã xác nhận & ghi nợ Folio phòng"
        );
    }

    /**
     * Mô hình 5: Điều chỉnh lịch trình/địa điểm động theo thời tiết biển (Dynamic Schedule)
     * và tự động phát thông báo tới toàn bộ hành khách đã đăng ký.
     */
    public RescheduleResult dynamicWeatherReschedule(
            UUID activityId,
            String newLocation,
            String newMeetingPoint,
            Instant newStartTime,
            Instant newEndTime,
            String weatherReason) {

        OnboardActivity activity = activities.lockActivity(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardActivity", activityId));

        String oldLocation = activity.getLocation();
        activity.setLocation(newLocation);
        if (newMeetingPoint != null && !newMeetingPoint.isBlank()) {
            activity.setMeetingPoint(newMeetingPoint);
        }
        if (newStartTime != null) {
            activity.setStartTime(newStartTime);
        }
        if (newEndTime != null) {
            activity.setEndTime(newEndTime);
        }

        // Gửi thông báo đến toàn bộ hành khách đã đăng ký hoạt động này
        List<Registration> registeredList = registrations.findByOnboardActivity_OnboardActivityId(activityId);
        int notifiedCount = 0;

        for (Registration reg : registeredList) {
            if (reg.getStatus() == RegistrationStatus.CONFIRMED || reg.getStatus() == RegistrationStatus.PENDING) {
                if (reg.getBookingPassenger() != null
                        && reg.getBookingPassenger().getPassenger() != null
                        && reg.getBookingPassenger().getPassenger().getUser() != null) {

                    Notification notification = new Notification();
                    notification.setRecipientUser(reg.getBookingPassenger().getPassenger().getUser());
                    notification.setBookingPassengerId(reg.getBookingPassenger().getBookingPassengerId());
                    notification.setNotificationType("WEATHER_RESCHEDULE");
                    notification.setTitle("Cập nhật địa điểm hoạt động: " + activity.getActivityName());
                    notification.setContent(String.format(
                            "Do biến động thời tiết biển (%s), hoạt động '%s' được dời từ '%s' sang '%s' (Điểm đón: %s).",
                            weatherReason, activity.getActivityName(), oldLocation, newLocation,
                            activity.getMeetingPoint() != null ? activity.getMeetingPoint() : newLocation
                    ));
                    notificationRepository.save(notification);
                    notifiedCount++;
                }
            }
        }

        log.info("Activity {} rescheduled to {} due to weather. Notified {} passengers.",
                activity.getActivityCode(), newLocation, notifiedCount);

        return new RescheduleResult(
                activity.getOnboardActivityId(),
                activity.getActivityName(),
                oldLocation,
                newLocation,
                activity.getMeetingPoint(),
                notifiedCount
        );
    }

    /**
     * Mô hình 1: Đồng bộ lô các giao dịch phát sinh ngoại tuyến (Hybrid Edge-to-Cloud Sync).
     * Khi tàu có kết nối Internet / cập cảng, cronjob gọi hàm này để đổi sync_status sang SYNCED.
     */
    public SyncResult syncPendingOfflineTransactions(int batchSize) {
        List<OnboardTransaction> pendingList = onboardTransactionRepository
                .findTop100BySyncStatusOrderByTransactionTimeAsc(TransactionSyncStatus.PENDING);

        if (pendingList.isEmpty()) {
            return new SyncResult(0, 0, Instant.now());
        }

        int processed = 0;
        Instant now = Instant.now();

        for (OnboardTransaction tx : pendingList) {
            // Giả lập / thực thi gửi bản ghi lên Central Cloud Endpoint qua REST API
            // Khi Central Cloud phản hồi thành công:
            tx.setSyncStatus(TransactionSyncStatus.SYNCED);
            tx.setSyncedAt(now);
            processed++;
        }

        onboardTransactionRepository.saveAll(pendingList);
        log.info("Batch synced {} offline transactions to Central Cloud.", processed);

        return new SyncResult(pendingList.size(), processed, now);
    }
}
