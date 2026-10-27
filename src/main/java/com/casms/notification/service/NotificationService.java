package com.casms.notification.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.dto.ImpactNotificationAggregateResponse;
import com.casms.notification.dto.NotificationResponse;
import com.casms.notification.entity.ImpactNotificationOutbox;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.ImpactNotificationOutboxRepository;
import com.casms.notification.repository.NotificationRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseImpactTask;
import com.casms.ship.repository.CruiseImpactTaskRepository;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ImpactNotificationOutboxRepository outboxRepository;
    private final BookingRepository bookingRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final CruiseRepository cruiseRepository;
    private final CruiseImpactTaskRepository cruiseImpactTaskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(CasmsPrincipal principal) {
        UUID userId = requireUserId(principal);
        return notificationRepository.findByRecipientUserUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, CasmsPrincipal principal) {
        UUID userId = requireUserId(principal);
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipientUserUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toResponse(notificationRepository.save(notification));
    }

    /**
     * BE-19: Phát thông báo tác động tới toàn bộ hành khách của chuyến đi bị ảnh hưởng (Outbox Pattern)
     * Đảm bảo tính Idempotent trên từng (eventIdentity, bookingId, notificationType) để chống gửi trùng lặp.
     * Tách bạch rõ giữa "task created" và "customer notified" (chỉ RESOLVED task khi đã phát thành công).
     */
    @Transactional
    public ImpactNotificationAggregateResponse dispatchImpactNotifications(UUID cruiseId, String eventIdentity, UUID actorId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        String effectiveEventIdentity = (eventIdentity != null && !eventIdentity.isBlank())
                ? eventIdentity
                : "EVT-" + cruise.getCruiseCode() + "-IMPACT-" + System.currentTimeMillis();

        List<Booking> activeBookings = bookingRepository.findByCruiseCruiseIdAndBookingStatusNot(cruiseId, BookingStatus.Cancelled);

        // Tìm CruiseImpactTask liên quan nếu có
        CruiseImpactTask notificationTask = cruiseImpactTaskRepository
                .findByCruiseCruiseIdAndCategory(cruiseId, "PASSENGER_NOTIFICATION")
                .stream().findFirst().orElse(null);

        long sentCount = 0;
        for (Booking booking : activeBookings) {
            User recipient = booking.getBookedByUser();
            if (recipient == null) continue;

            String notificationType = "CRUISE_SCHEDULE_CHANGE";
            if (outboxRepository.existsByEventIdentityAndBooking_BookingIdAndNotificationType(
                    effectiveEventIdentity, booking.getBookingId(), notificationType)) {
                // Đã phát thông báo trước đó cho sự kiện này (Idempotent) -> Không gửi trùng
                continue;
            }

            String title = String.format("[CASMS] Thông báo điều chỉnh chuyến đi %s (%s)",
                    cruise.getCruiseName(), cruise.getCruiseCode());
            String content = String.format("Kính gửi Quý khách,\nChuyến đi %s (Mã đặt chỗ: %s) có sự điều chỉnh lịch trình khởi hành sang thời gian mới: %s.\nQuý khách có thể yêu cầu đổi lịch hoặc hoàn tiền theo điều kiện hợp đồng đã ký kết.\nTrân trọng cảm ơn.",
                    cruise.getCruiseName(), booking.getBookingReference(), cruise.getDepartureDatetime());

            // 1. Lưu bản ghi Outbox đáng tin cậy với Channel Semantics rõ ràng
            ImpactNotificationOutbox outbox = ImpactNotificationOutbox.builder()
                    .cruise(cruise)
                    .task(notificationTask)
                    .booking(booking)
                    .recipientUser(recipient)
                    .eventIdentity(effectiveEventIdentity)
                    .notificationType(notificationType)
                    .title(title)
                    .content(content)
                    .status("IN_APP_DELIVERED")
                    .deliveryChannel("IN_APP")
                    .deliverySemantics("IN_APP_DELIVERED (Thông báo gửi vào hộp thư trong ứng dụng của hành khách; phạm vi chính thức in-app only)")
                    .inAppDeliveredAt(Instant.now())
                    .sentAt(Instant.now())
                    .build();
            outboxRepository.save(outbox);

            // 2. Đồng bộ vào bảng Notification để hiển thị cho User
            Notification notif = new Notification();
            notif.setRecipientUser(recipient);
            notif.setNotificationType(notificationType);
            notif.setTitle(title);
            notif.setContent(content);
            notificationRepository.save(notif);

            sentCount++;
        }

        // Tách bạch rõ rệt: task CREATED != in-app notified != external delivered != ACK
        if (notificationTask != null) {
            long totalBookings = activeBookings.size();
            long totalAck = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "ACK");
            boolean allAck = totalAck >= totalBookings && totalBookings > 0;

            if (allAck) {
                notificationTask.setStatus("RESOLVED");
                notificationTask.setResolvedAt(Instant.now());
                if (actorId != null) {
                    userRepository.findById(actorId).ifPresent(notificationTask::setResolvedBy);
                }
                notificationTask.setEvidence(String.format("Toàn bộ %d đơn đặt chỗ đã xác nhận/đọc thông báo (ACK hoàn tất).", totalBookings));
                notificationTask.setResolutionNotes("Khách hàng đã nhận và xác nhận đầy đủ thông báo tác động.");
            } else {
                notificationTask.setStatus("IN_PROGRESS");
                notificationTask.setEvidence(String.format("Đã phát %d thông báo in-app (IN_APP_DELIVERED). Kênh: IN_APP (in-app only). Đang chờ hành khách xác nhận đọc (ACK: %d/%d).",
                        sentCount, totalAck, totalBookings));
                notificationTask.setResolutionNotes("Thông báo tác động đã được đưa vào hộp thư In-App; chưa hoàn tất ACK từ khách hàng.");
            }
            cruiseImpactTaskRepository.save(notificationTask);
        }

        log.info("Dispatched {} impact notifications for cruise {}", sentCount, cruise.getCruiseCode());
        return getImpactAggregate(cruiseId);
    }

    /**
     * BE-19 / BE-H15: Thống kê tổng hợp trạng thái phát thông báo tác động (Safe Aggregate, không để lộ PII)
     */
    @Transactional(readOnly = true)
    public ImpactNotificationAggregateResponse getImpactAggregate(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        long totalBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, BookingStatus.Confirmed)
                + bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, BookingStatus.Draft);
        long totalPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);

        long inAppDeliveredCount = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "IN_APP_DELIVERED");
        long sentCount = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "SENT") + inAppDeliveredCount;
        long pendingCount = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "PENDING");
        long failedCount = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "FAILED");
        long ackCount = outboxRepository.countByCruise_CruiseIdAndStatus(cruiseId, "ACK");

        String deliveryStatus;
        if (sentCount == 0 && ackCount == 0) {
            deliveryStatus = "NOT_STARTED";
        } else if (pendingCount > 0 || (sentCount + ackCount < totalBookings)) {
            deliveryStatus = "IN_PROGRESS";
        } else {
            deliveryStatus = "COMPLETED";
        }

        Instant lastDispatched = outboxRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(ImpactNotificationOutbox::getSentAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        return ImpactNotificationAggregateResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .totalImpactedBookings(totalBookings)
                .totalImpactedPassengers(totalPassengers)
                .notificationsSentCount(sentCount)
                .inAppDeliveredCount(inAppDeliveredCount)
                .notificationsPendingCount(pendingCount)
                .notificationsFailedCount(failedCount)
                .notificationsAcknowledgedCount(ackCount)
                .deliveryStatus(deliveryStatus)
                .deliveryChannel("IN_APP")
                .deliverySemantics("Phạm vi chính thức gửi thông báo In-App. Ghi nhận thành công khi bản ghi Notification được lưu vào hệ thống.")
                .lastDispatchedAt(lastDispatched)
                .build();
    }

    /**
     * BE-19 / BE-H15: Khách hàng xác nhận đã nhận/đọc thông báo tác động (ACK)
     */
    @Transactional
    public NotificationResponse acknowledgeNotification(UUID notificationId, CasmsPrincipal principal) {
        UUID userId = requireUserId(principal);
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipientUserUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        Instant now = Instant.now();
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
            notificationRepository.save(notification);
        }

        // Cập nhật ACK vào Outbox nếu tương ứng
        List<ImpactNotificationOutbox> outboxes = outboxRepository.findByRecipientUser_UserIdAndAcknowledgedAtIsNull(userId);
        for (ImpactNotificationOutbox o : outboxes) {
            o.setStatus("ACK");
            o.setAcknowledgedAt(now);
            outboxRepository.save(o);
        }

        // Tự động chuyển task sang RESOLVED nếu tất cả đơn đặt chỗ liên quan đã ACK
        outboxes.stream().map(ImpactNotificationOutbox::getTask).filter(Objects::nonNull).distinct().forEach(task -> {
            long remainingUnack = outboxRepository.countByCruise_CruiseIdAndStatus(task.getCruise().getCruiseId(), "IN_APP_DELIVERED")
                    + outboxRepository.countByCruise_CruiseIdAndStatus(task.getCruise().getCruiseId(), "SENT");
            if (remainingUnack == 0) {
                task.setStatus("RESOLVED");
                task.setResolvedAt(now);
                task.setEvidence("Tất cả hành khách đã xác nhận đọc thông báo (ACK hoàn tất).");
                cruiseImpactTaskRepository.save(task);
            }
        });

        return toResponse(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(), notification.getBookingPassengerId(),
                notification.getNotificationType(), notification.getTitle(), notification.getContent(),
                notification.getCreatedAt(), notification.getReadAt());
    }

    private UUID requireUserId(CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication is required");
        }
        return principal.getUserId();
    }
}

