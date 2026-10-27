package com.casms.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo cáo tổng hợp an toàn về tiến độ phát thông báo tác động (BE-19 Safe Aggregate, không lộ PII)")
public class ImpactNotificationAggregateResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "Tổng số booking bị ảnh hưởng bởi thay đổi")
    private long totalImpactedBookings;

    @Schema(description = "Tổng số hành khách bị ảnh hưởng")
    private long totalImpactedPassengers;

    @Schema(description = "Số lượng thông báo đã phát thành công (SENT)")
    private long notificationsSentCount;

    @Schema(description = "Số lượng thông báo đang chờ phát (PENDING)")
    private long notificationsPendingCount;

    @Schema(description = "Số lượng thông báo phát thất bại (FAILED)")
    private long notificationsFailedCount;

    @Schema(description = "Số lượng thông báo đã được khách hàng xác nhận/đọc (ACK)")
    private long notificationsAcknowledgedCount;

    @Schema(description = "Trạng thái tiến độ tổng thể (NOT_STARTED, IN_PROGRESS, COMPLETED)")
    private String deliveryStatus;

    @Schema(description = "Thời điểm phát thông báo gần nhất")
    private Instant lastDispatchedAt;
}
