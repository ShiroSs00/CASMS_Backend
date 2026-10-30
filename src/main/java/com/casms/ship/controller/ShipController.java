package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipCreateRequest;
import com.casms.ship.dto.request.ShipUpdateRequest;
import com.casms.ship.dto.response.CruiseSummaryResponse;
import com.casms.ship.dto.response.ShipCapacitySummaryResponse;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.dto.response.ShipReadinessAssessmentResponse;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.service.ShipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ship Management", description = "Quản lý thông tin và vận hành tàu du lịch")
@RestController
@RequestMapping("/ships")
@RequiredArgsConstructor
public class ShipController {

    private final ShipService service;

    @Operation(summary = "Tạo tàu mới", description = "Chọn modelKey và assetVersion đã tải lên để tái sử dụng mô hình. Thông tin mô hình có sẵn được lấy tự động; chỉ bổ sung trường còn thiếu. Mã tàu và thời điểm tạo tự sinh. Có thể tạo nhiều tàu từ cùng mô hình mà không tải lại file.")
    @PreAuthorize("hasAuthority('SHIP_CREATE') and (#request.modelKey == null or hasAuthority('SHIP_MODEL_CREATE'))")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipResponse> create(@RequestBody @Valid ShipCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách tàu", description = "Lấy danh sách tàu trong hệ thống (hỗ trợ phân trang và sắp xếp)")
    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping
    public ApiResponse<Page<ShipResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Chi tiết tàu", description = "Xem thông tin chi tiết của một tàu theo ID")
    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/{shipId}")
    public ApiResponse<ShipResponse> detail(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        return ApiResponse.success(service.detail(shipId));
    }

    @Operation(summary = "Cập nhật tàu", description = "Cập nhật các thông tin cơ bản của tàu (tên, sức chứa, số boong, ngày hạ thủy)")
    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PatchMapping("/{shipId}")
    public ApiResponse<ShipResponse> update(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId,
            @RequestBody @Valid ShipUpdateRequest request) {
        return ApiResponse.success(service.update(shipId, request));
    }

    @Operation(summary = "Xóa tàu chưa sử dụng", description = "Xóa tàu nếu tàu chưa được gán cho bất kỳ chuyến đi (Cruise) nào")
    @PreAuthorize("hasAuthority('SHIP_DELETE')")
    @DeleteMapping("/{shipId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        service.deleteUnused(shipId);
        return ApiResponse.success("Deleted unused ship successfully", null);
    }

    @Operation(summary = "Thay đổi trạng thái tàu", description = "Cập nhật trạng thái tàu (ACTIVE, MAINTENANCE, INACTIVE). Chặn chuyển sang MAINTENANCE/INACTIVE nếu tàu đang trong chuyến đi")
    @PreAuthorize("hasAuthority('SHIP_STATUS_CHANGE')")
    @PostMapping("/{shipId}/status")
    public ApiResponse<ShipResponse> changeStatus(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId,
            @Parameter(description = "Trạng thái mới", required = true) @RequestParam ShipStatus status) {
        return ApiResponse.success(service.changeStatus(shipId, status));
    }

    @Operation(summary = "Kiểm tra lịch trình khả dụng", description = "Kiểm tra xem tàu có rảnh trong khoảng thời gian chỉ định hay không")
    @PreAuthorize("hasAuthority('SHIP_SCHEDULE_CHECK')")
    @GetMapping("/{shipId}/availability")
    public ApiResponse<Boolean> checkScheduleAvailability(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId,
            @Parameter(description = "Thời điểm bắt đầu (ISO-8601)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @Parameter(description = "Thời điểm kết thúc (ISO-8601)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ApiResponse.success(service.checkScheduleAvailability(shipId, start, end));
    }

    @Operation(summary = "Tổng hợp sức chứa của tàu", description = "Lấy báo cáo tổng hợp về số tầng, tổng cabin thực tế và số cabin còn trống")
    @PreAuthorize("hasAuthority('SHIP_CAPACITY_SUMMARY')")
    @GetMapping("/{shipId}/capacity-summary")
    public ApiResponse<ShipCapacitySummaryResponse> getCapacitySummary(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        return ApiResponse.success(service.getCapacitySummary(shipId));
    }

    @Operation(summary = "Danh sách chuyến đi bị ảnh hưởng", description = "Tìm các chuyến đi của tàu nằm trong khoảng thời gian chỉ định (thường dùng khi cần đưa tàu vào bảo trì)")
    @PreAuthorize("hasAuthority('SHIP_AFFECTED_CRUISES')")
    @GetMapping("/{shipId}/affected-cruises")
    public ApiResponse<List<CruiseSummaryResponse>> getAffectedCruises(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId,
            @Parameter(description = "Thời điểm bắt đầu (ISO-8601)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @Parameter(description = "Thời điểm kết thúc (ISO-8601)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ApiResponse.success(service.getAffectedCruises(shipId, start, end));
    }

    @Operation(summary = "Chuyến đi hiện tại của tàu", description = "Lấy chuyến đi đang diễn ra (IN_PROGRESS) của tàu tại thời điểm hiện tại")
    @PreAuthorize("hasAuthority('SHIP_CURRENT_CRUISE')")
    @GetMapping("/{shipId}/current-cruise")
    public ApiResponse<CruiseSummaryResponse> getCurrentCruise(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        return ApiResponse.success(service.getCurrentCruise(shipId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của tàu", description = "Xác minh các ràng buộc cấu hình của tàu (ví dụ: số boong thực tế không vượt quá số boong tối đa)")
    @PreAuthorize("hasAuthority('SHIP_VALIDATE')")
    @PostMapping("/{shipId}/validate")
    public ApiResponse<Void> validateShip(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        service.validateShip(shipId);
        return ApiResponse.success("Ship validation passed", null);
    }

    @Operation(summary = "Kiểm tra sẵn sàng vận hành", description = "Xác minh tàu đã đủ điều kiện hoạt động: trạng thái ACTIVE, có ít nhất 1 boong và 1 cabin")
    @PreAuthorize("hasAuthority('SHIP_READINESS')")
    @GetMapping("/{shipId}/readiness")
    public ApiResponse<Boolean> validateOperationalReadiness(
            @Parameter(description = "ID của tàu", required = true) @PathVariable UUID shipId) {
        return ApiResponse.success(service.validateOperationalReadiness(shipId));
    }

    @PreAuthorize("hasAuthority('SHIP_READINESS')")
    @GetMapping("/{shipId}/readiness-assessment")
    public ApiResponse<ShipReadinessAssessmentResponse> assessOperationalReadiness(@PathVariable UUID shipId) {
        return ApiResponse.success(service.assessOperationalReadiness(shipId));
    }
}
