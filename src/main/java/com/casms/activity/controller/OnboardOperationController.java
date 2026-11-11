package com.casms.activity.controller;

import com.casms.activity.service.OnboardOperationService;
import com.casms.activity.service.OnboardOperationService.ActivityBookingResult;
import com.casms.activity.service.OnboardOperationService.RescheduleResult;
import com.casms.activity.service.OnboardOperationService.SyncResult;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboard-operations")
@RequiredArgsConstructor
@Tag(
        name = "Onboard Operations Management",
        description = "Vận hành dịch vụ giải trí & lưu trú du thuyền thuần Spring Boot & PostgreSQL (Không cần Kafka)")
public class OnboardOperationController {

    private final OnboardOperationService operationService;

    public record BookActivityRequest(
            @NotNull UUID bookingPassengerId,
            @NotNull UUID activityId,
            @Positive Integer quantity
    ) {}

    public record WeatherRescheduleRequest(
            @NotBlank String newLocation,
            String newMeetingPoint,
            Instant newStartTime,
            Instant newEndTime,
            @NotBlank String reason
    ) {}

    @Operation(
            summary = "Đặt chỗ giải trí kết hợp Folio & Quota",
            description = "Áp dụng Mô hình 2 (Cashless Folio), Mô hình 3 (Slot Quota Lock), Mô hình 4 (All-Inclusive vs Add-on)")
    @PostMapping("/activities/book")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ActivityBookingResult> bookActivity(
            @Valid @RequestBody BookActivityRequest request,
            Authentication authentication) {

        CasmsPrincipal principal = (CasmsPrincipal) authentication.getPrincipal();
        ActivityBookingResult result = operationService.bookActivityWithFolio(
                request.bookingPassengerId(),
                request.activityId(),
                request.quantity() != null ? request.quantity() : 1,
                principal.getUser()
        );
        return ApiResponse.success(result);
    }

    @Operation(
            summary = "Điều chỉnh lịch hoạt động do thời tiết biển",
            description = "Áp dụng Mô hình 5 (Dynamic Schedule) - Dời địa điểm và tự động thông báo tới hành khách")
    @PostMapping("/activities/{activityId}/weather-reschedule")
    @PreAuthorize("hasAnyRole('CRUISE_STAFF', 'SHIP_OPERATOR', 'ACTIVITY_SERVICE_MANAGER', 'ADMIN')")
    public ApiResponse<RescheduleResult> rescheduleActivity(
            @PathVariable UUID activityId,
            @Valid @RequestBody WeatherRescheduleRequest request) {

        RescheduleResult result = operationService.dynamicWeatherReschedule(
                activityId,
                request.newLocation(),
                request.newMeetingPoint(),
                request.newStartTime(),
                request.newEndTime(),
                request.reason()
        );
        return ApiResponse.success(result);
    }

    @Operation(
            summary = "Kích hoạt đồng bộ giao dịch ngoại tuyến lên Cloud",
            description = "Áp dụng Mô hình 1 (Hybrid Edge-to-Cloud) - Quét lô giao dịch PENDING và chuyển sang SYNCED")
    @PostMapping("/sync/batch")
    @PreAuthorize("hasAnyRole('SHIP_OPERATOR', 'ADMIN')")
    public ApiResponse<SyncResult> triggerSync(
            @RequestParam(defaultValue = "100") int batchSize) {

        SyncResult result = operationService.syncPendingOfflineTransactions(batchSize);
        return ApiResponse.success(result);
    }
}
