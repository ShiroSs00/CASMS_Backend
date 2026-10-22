package com.casms.notification.controller;

import com.casms.notification.service.NotificationService;
import com.casms.notification.dto.NotificationResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "Notifications", description = "Quản lý thông báo người dùng và hành khách")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public ApiResponse<List<NotificationResponse>> listMine(@AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.listMine(principal));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId,
                                               @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.markRead(notificationId, principal));
    }
}
