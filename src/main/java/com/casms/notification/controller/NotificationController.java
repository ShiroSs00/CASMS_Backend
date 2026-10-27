package com.casms.notification.controller;

import com.casms.notification.service.NotificationService;
import com.casms.notification.dto.NotificationResponse;
import com.casms.notification.dto.ImpactNotificationAggregateResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "Notifications", description = "Quản lý thông báo người dùng và hành khách")
@RestController
@RequestMapping({"/notification", "/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "Danh sách thông báo của người dùng hiện tại")
    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public ApiResponse<List<NotificationResponse>> listMine(@AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.listMine(principal));
    }

    @Operation(summary = "Đánh dấu đã đọc thông báo")
    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId,
                                               @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.markRead(notificationId, principal));
    }

    @Operation(summary = "Xác nhận đã nhận/đọc thông báo tác động (ACK - BE-19)")
    @PostMapping("/{notificationId}/ack")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public ApiResponse<NotificationResponse> acknowledgeNotification(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.acknowledgeNotification(notificationId, principal));
    }

    @Operation(summary = "Thống kê an toàn kết quả phát thông báo tác động (Safe Aggregate - BE-19, không lộ PII)")
    @GetMapping("/impact/{cruiseId}/aggregate")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'OPERATIONAL_ISSUE_READ', 'NOTIFICATION_READ')")
    public ApiResponse<ImpactNotificationAggregateResponse> getImpactAggregate(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getImpactAggregate(cruiseId));
    }

    @Operation(summary = "Kích hoạt phát thông báo tác động cho các đơn đặt chỗ bị ảnh hưởng (Outbox Dispatch - BE-19)")
    @PostMapping("/impact/{cruiseId}/dispatch")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_RESOLVE')")
    public ApiResponse<ImpactNotificationAggregateResponse> dispatchImpactNotifications(
            @PathVariable UUID cruiseId,
            @RequestParam(required = false) String eventIdentity,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.dispatchImpactNotifications(cruiseId, eventIdentity, actorId));
    }
}

