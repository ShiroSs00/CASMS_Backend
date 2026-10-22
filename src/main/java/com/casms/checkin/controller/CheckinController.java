package com.casms.checkin.controller;

import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.service.CheckinService;
import com.casms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Check-in & Boarding", description = "Quản lý thủ tục check-in, định danh hành khách và cấp thẻ lên tàu (Boarding Pass)")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService service;

    @Operation(summary = "Bắt đầu thủ tục check-in cho hành khách", description = "Khởi tạo hồ sơ check-in và khai báo thông tin liên hệ khẩn cấp")
    @PreAuthorize("hasAuthority('CHECKIN_CREATE')")
    @PostMapping("/passenger/{passengerId}/start")
    public ApiResponse<CruiseCheckin> startCheckin(
            @Parameter(description = "ID hành khách đặt chỗ", required = true) @PathVariable UUID passengerId,
            @RequestParam(required = false) String emergencyContactName,
            @RequestParam(required = false) String emergencyContactPhone,
            Authentication authentication) {
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        return ApiResponse.success("Check-in started successfully",
                service.startCheckin(passengerId, emergencyContactName, emergencyContactPhone,
                        principal.getUserId(), principal.isStaff()));
    }

    @Operation(summary = "Hoàn tất thủ tục check-in và sẵn sàng lên tàu", description = "Đánh dấu hành khách đã hoàn tất kiểm tra và chuyển sang trạng thái BoardingReady")
    @PreAuthorize("hasAuthority('CHECKIN_UPDATE')")
    @PostMapping("/passenger/{passengerId}/complete")
    public ApiResponse<CruiseCheckin> completeCheckin(
            @Parameter(description = "ID hành khách đặt chỗ", required = true) @PathVariable UUID passengerId,
            Authentication authentication) {
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        return ApiResponse.success("Check-in completed successfully",
                service.completeCheckin(passengerId, principal.getUserId(), principal.isStaff()));
    }

    @Operation(summary = "Kiểm tra trạng thái check-in của hành khách", description = "Tra cứu tiến trình check-in hiện tại của hành khách")
    @PreAuthorize("hasAuthority('CHECKIN_READ')")
    @GetMapping("/passenger/{passengerId}/status")
    public ApiResponse<CruiseCheckin> getStatus(
            @Parameter(description = "ID hành khách đặt chỗ", required = true) @PathVariable UUID passengerId,
            Authentication authentication) {
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.getStatus(passengerId, principal.getUserId(), principal.isStaff()));
    }
}
