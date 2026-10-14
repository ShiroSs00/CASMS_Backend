package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.DestinationCreateRequest;
import com.casms.ship.dto.request.DestinationUpdateRequest;
import com.casms.ship.dto.response.DestinationResponse;
import com.casms.ship.dto.response.PortSummaryResponse;
import com.casms.ship.service.DestinationService;
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

@Tag(name = "Destination Management", description = "Quản lý điểm đến du lịch")
@RestController
@RequestMapping("/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService service;

    @Operation(summary = "Tạo điểm đến mới", description = "Đăng ký điểm đến du lịch mới vào hệ thống")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DestinationResponse> create(@RequestBody @Valid DestinationCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách điểm đến", description = "Lấy danh sách điểm đến (hỗ trợ phân trang)")
    @GetMapping
    public ApiResponse<Page<DestinationResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Danh sách điểm đến hoạt động", description = "Lấy danh sách tất cả điểm đến đang ACTIVE (không phân trang)")
    @GetMapping("/active")
    public ApiResponse<List<DestinationResponse>> listActive() {
        return ApiResponse.success(service.listAllActive());
    }

    @Operation(summary = "Chi tiết điểm đến", description = "Lấy thông tin chi tiết của một điểm đến theo ID")
    @GetMapping("/{destinationId}")
    public ApiResponse<DestinationResponse> detail(
            @Parameter(description = "ID của điểm đến", required = true) @PathVariable UUID destinationId) {
        return ApiResponse.success(service.detail(destinationId));
    }

    @Operation(summary = "Cập nhật điểm đến", description = "Cập nhật thông tin điểm đến (tên, mô tả)")
    @PatchMapping("/{destinationId}")
    public ApiResponse<DestinationResponse> update(
            @Parameter(description = "ID của điểm đến", required = true) @PathVariable UUID destinationId,
            @RequestBody @Valid DestinationUpdateRequest request) {
        return ApiResponse.success(service.update(destinationId, request));
    }

    @Operation(summary = "Xóa điểm đến chưa sử dụng", description = "Xóa điểm đến nếu chưa được gán cho bất kỳ điểm dừng chuyến đi nào")
    @DeleteMapping("/{destinationId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của điểm đến", required = true) @PathVariable UUID destinationId) {
        service.deleteUnused(destinationId);
        return ApiResponse.success("Deleted unused destination successfully", null);
    }

    @Operation(summary = "Danh sách cảng theo điểm đến", description = "Lấy danh sách các cảng thuộc về điểm đến này")
    @GetMapping("/{destinationId}/ports")
    public ApiResponse<List<PortSummaryResponse>> listPortsByDestination(
            @Parameter(description = "ID của điểm đến", required = true) @PathVariable UUID destinationId) {
        return ApiResponse.success(service.listPortsByDestination(destinationId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ điểm đến", description = "Xác minh tính hợp lệ của dữ liệu điểm đến")
    @PostMapping("/{destinationId}/validate")
    public ApiResponse<Void> validateDestination(
            @Parameter(description = "ID của điểm đến", required = true) @PathVariable UUID destinationId) {
        service.validateDestination(destinationId);
        return ApiResponse.success("Destination validation passed", null);
    }
}