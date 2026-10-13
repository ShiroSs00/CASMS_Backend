package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.CabinCreateRequest;
import com.casms.ship.dto.request.CabinSearchRequest;
import com.casms.ship.dto.request.CabinUpdateRequest;
import com.casms.ship.dto.response.CabinAssignmentHistoryResponse;
import com.casms.ship.dto.response.CabinSummaryResponse;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.service.CabinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Cabin Management", description = "Quản lý phòng nghỉ (cabin) trên tàu du lịch")
@RestController
@RequestMapping("/cabins")
@RequiredArgsConstructor
public class CabinController {

    private final CabinService service;

    @Operation(summary = "Tạo phòng mới", description = "Tạo cabin mới trên tầng đã chọn (kiểm tra tầng thuộc tàu và không trùng số phòng trên tàu)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CabinSummaryResponse> create(@RequestBody @Valid CabinCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách phòng", description = "Lấy danh sách cabin trong hệ thống (hỗ trợ phân trang)")
    @GetMapping
    public ApiResponse<Page<CabinSummaryResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Chi tiết phòng", description = "Lấy thông tin chi tiết của một cabin theo ID")
    @GetMapping("/{cabinId}")
    public ApiResponse<CabinSummaryResponse> detail(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        return ApiResponse.success(service.detail(cabinId));
    }

    @Operation(summary = "Cập nhật phòng", description = "Cập nhật loại phòng, sức chứa hoặc thuộc tính hỗ trợ người khuyết tật")
    @PatchMapping("/{cabinId}")
    public ApiResponse<CabinSummaryResponse> update(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId,
            @RequestBody @Valid CabinUpdateRequest request) {
        return ApiResponse.success(service.update(cabinId, request));
    }

    @Operation(summary = "Xóa phòng chưa sử dụng", description = "Xóa phòng nếu phòng chưa từng được gán cho hành khách trong bất kỳ booking nào")
    @DeleteMapping("/{cabinId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        service.deleteUnused(cabinId);
        return ApiResponse.success("Deleted unused cabin successfully", null);
    }

    @Operation(summary = "Tìm kiếm phòng trống", description = "Tìm các cabin có trạng thái AVAILABLE theo bộ lọc: tàu, tầng, loại phòng, sức chứa tối thiểu, hỗ trợ người khuyết tật")
    @PostMapping("/search")
    public ApiResponse<List<CabinSummaryResponse>> searchAvailable(@RequestBody CabinSearchRequest request) {
        return ApiResponse.success(service.searchAvailable(request));
    }

    @Operation(summary = "Kiểm tra sức chứa của phòng", description = "Kiểm tra xem phòng có đáp ứng được số lượng khách yêu cầu hay không")
    @GetMapping("/{cabinId}/capacity-check")
    public ApiResponse<Boolean> checkCapacity(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId,
            @Parameter(description = "Số lượng khách cần ở", required = true) @RequestParam Integer requiredCapacity) {
        return ApiResponse.success(service.checkCapacity(cabinId, requiredCapacity));
    }

    @Operation(summary = "Kiểm tra tính tương thích của phòng", description = "Kiểm tra phòng có đáp ứng yêu cầu tiện ích (ví dụ: cần phòng hỗ trợ người khuyết tật) hay không")
    @GetMapping("/{cabinId}/compatibility-check")
    public ApiResponse<Boolean> checkCompatibility(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId,
            @Parameter(description = "Yêu cầu phòng hỗ trợ người khuyết tật (true/false)") @RequestParam(required = false) Boolean requireAccessible) {
        return ApiResponse.success(service.checkCompatibility(cabinId, requireAccessible));
    }

    @Operation(summary = "Thay đổi trạng thái vận hành phòng", description = "Đổi trạng thái phòng (AVAILABLE, OCCUPIED, MAINTENANCE, OUT_OF_SERVICE). Chặn đưa vào bảo trì nếu phòng đang có khách ở")
    @PostMapping("/{cabinId}/status")
    public ApiResponse<CabinSummaryResponse> changeOperationalStatus(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId,
            @Parameter(description = "Trạng thái mới", required = true) @RequestParam CabinStatus status) {
        return ApiResponse.success(service.changeOperationalStatus(cabinId, status));
    }

    @Operation(summary = "Lịch sử gán phòng", description = "Xem danh sách lịch sử gán phòng cho hành khách theo thời gian")
    @GetMapping("/{cabinId}/assignments")
    public ApiResponse<List<CabinAssignmentHistoryResponse>> getAssignmentHistory(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        return ApiResponse.success(service.getAssignmentHistory(cabinId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của phòng", description = "Xác minh tầng của phòng thuộc đúng tàu mà phòng được đăng ký")
    @PostMapping("/{cabinId}/validate")
    public ApiResponse<Void> validateCabin(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        service.validateCabin(cabinId);
        return ApiResponse.success("Cabin validation passed", null);
    }

    @Operation(summary = "Kiểm tra phòng có đang trống", description = "Trả về true nếu phòng có trạng thái AVAILABLE, ngược lại false")
    @GetMapping("/{cabinId}/availability")
    public ApiResponse<Boolean> checkAvailability(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        return ApiResponse.success(service.checkAvailability(cabinId));
    }

    @Operation(summary = "Khóa phòng", description = "Khóa phòng từ AVAILABLE sang trạng thái OCCUPIED hoặc MAINTENANCE")
    @PostMapping("/{cabinId}/block")
    public ApiResponse<CabinSummaryResponse> blockCabin(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId,
            @Parameter(description = "Trạng thái khóa (chỉ chấp nhận OCCUPIED hoặc MAINTENANCE)", required = true) @RequestParam CabinStatus reasonStatus) {
        return ApiResponse.success(service.blockCabin(cabinId, reasonStatus));
    }

    @Operation(summary = "Mở khóa phòng", description = "Giải phóng phòng về trạng thái AVAILABLE")
    @PostMapping("/{cabinId}/release")
    public ApiResponse<CabinSummaryResponse> releaseCabin(
            @Parameter(description = "ID của phòng", required = true) @PathVariable UUID cabinId) {
        return ApiResponse.success(service.releaseCabin(cabinId));
    }
}