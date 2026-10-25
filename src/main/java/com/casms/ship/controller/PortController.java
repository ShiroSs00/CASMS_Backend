package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.PortCreateRequest;
import com.casms.ship.dto.request.PortUpdateRequest;
import com.casms.ship.dto.response.PortResponse;
import com.casms.ship.dto.response.TimeZoneOptionResponse;
import com.casms.ship.service.PortService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Port Management", description = "Quản lý cảng biển")
@RestController
@RequestMapping("/ports")
@RequiredArgsConstructor
public class
PortController {

    private final PortService service;

    @Operation(summary = "Danh sách múi giờ IANA", description = "Lấy danh sách các múi giờ IANA chuẩn cho dropdown (hỗ trợ tìm kiếm theo tên hoặc mã quốc gia)")
    @PreAuthorize("permitAll()")
    @GetMapping("/timezones")
    public ApiResponse<List<TimeZoneOptionResponse>> listTimeZones(
            @Parameter(description = "Từ khóa tìm kiếm múi giờ (ví dụ: Tokyo, Paris, New_York)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Mã quốc gia ISO-2 để gợi ý múi giờ (ví dụ: JP, FR, US)")
            @RequestParam(required = false) String countryCode) {
        return ApiResponse.success(service.listSupportedTimeZones(search, countryCode));
    }

    @Operation(summary = "Suy luận múi giờ theo quốc gia", description = "Lấy múi giờ mặc định dựa trên mã quốc gia (ISO 3166-1 alpha-2)")
    @PreAuthorize("permitAll()")
    @GetMapping("/timezones/resolve")
    public ApiResponse<TimeZoneOptionResponse> resolveTimeZone(
            @Parameter(description = "Mã quốc gia ISO-2 (ví dụ: JP, VN, SG, FR, US)", required = true)
            @RequestParam String countryCode) {
        return ApiResponse.success(service.resolveTimeZoneByCountry(countryCode));
    }

    @Operation(summary = "Tạo cảng mới", description = "Đăng ký cảng biển mới vào hệ thống")
    @PreAuthorize("hasAuthority('PORT_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortResponse> create(@RequestBody @Valid PortCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách cảng", description = "Lấy danh sách cảng (hỗ trợ phân trang)")
    @PreAuthorize("hasAuthority('PORT_READ')")
    @GetMapping
    public ApiResponse<Page<PortResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Danh sách cảng hoạt động", description = "Lấy danh sách tất cả cảng đang ACTIVE (không phân trang)")
    @PreAuthorize("permitAll()")
    @GetMapping("/active")
    public ApiResponse<List<PortResponse>> listActive() {
        return ApiResponse.success(service.listAllActive());
    }

    @Operation(summary = "Chi tiết cảng", description = "Lấy thông tin chi tiết của một cảng theo ID")
    @PreAuthorize("permitAll()")
    @GetMapping("/{portId}")
    public ApiResponse<PortResponse> detail(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        return ApiResponse.success(service.detail(portId));
    }

    @Operation(summary = "Cập nhật cảng", description = "Cập nhật thông tin cảng (tên, tọa độ, múi giờ)")
    @PreAuthorize("hasAuthority('PORT_UPDATE')")
    @PatchMapping("/{portId}")
    public ApiResponse<PortResponse> update(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId,
            @RequestBody @Valid PortUpdateRequest request) {
        return ApiResponse.success(service.update(portId, request));
    }

    @Operation(summary = "Xóa cảng chưa sử dụng", description = "Xóa cảng nếu cảng chưa được gán cho bất kỳ điểm dừng chuyến đi nào")
    @PreAuthorize("hasAuthority('PORT_DELETE_UNUSED')")
    @DeleteMapping("/{portId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        service.deleteUnused(portId);
        return ApiResponse.success("Deleted unused port successfully", null);
    }

    @Operation(summary = "Kích hoạt cảng", description = "Chuyển trạng thái cảng sang ACTIVE")
    @PreAuthorize("hasAuthority('PORT_ACTIVATE')")
    @PostMapping("/{portId}/activate")
    public ApiResponse<PortResponse> activate(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        return ApiResponse.success(service.activate(portId));
    }

    @Operation(summary = "Vô hiệu hóa cảng", description = "Chuyển trạng thái cảng sang INACTIVE")
    @PreAuthorize("hasAuthority('PORT_DEACTIVATE')")
    @PostMapping("/{portId}/deactivate")
    public ApiResponse<PortResponse> deactivate(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        return ApiResponse.success(service.deactivate(portId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ cảng", description = "Xác minh tính hợp lệ của dữ liệu cảng")
    @PreAuthorize("hasAuthority('PORT_VALIDATE')")
    @PostMapping("/{portId}/validate")
    public ApiResponse<Void> validatePort(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        service.validatePort(portId);
        return ApiResponse.success("Port validation passed", null);
    }

    @Operation(summary = "Kiểm tra sẵn sàng vận hành", description = "Kiểm tra cảng có đang ACTIVE và sẵn sàng cho hoạt động không")
    @PreAuthorize("hasAuthority('PORT_READ')")
    @GetMapping("/{portId}/availability")
    public ApiResponse<Boolean> checkOperationalAvailability(
            @Parameter(description = "ID của cảng", required = true) @PathVariable UUID portId) {
        return ApiResponse.success(service.checkOperationalAvailability(portId));
    }
}