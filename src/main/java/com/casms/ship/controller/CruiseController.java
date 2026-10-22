package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.CruiseCancelRequest;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseManifestResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.CruiseTourPackageResponse;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.service.CruisePackageService;
import com.casms.ship.service.CruiseService;
import com.casms.ship.service.CruiseStaffService;
import com.casms.security.principal.CasmsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Cruise Management", description = "Quản lý kế hoạch và vòng đời chuyến đi du thuyền (Cruise)")
@RestController
@RequestMapping("/cruise")
@RequiredArgsConstructor
public class CruiseController {

    private final CruiseService service;
    private final CruiseStaffService staffService;
    private final CruisePackageService packageService;

    @Operation(summary = "Tạo chuyến đi mới", description = "Khởi tạo bản ghi chuyến đi (mặc định ở trạng thái DRAFT). Có thể gán tàu ngay hoặc gán sau.")
    @PreAuthorize("hasAuthority('CRUISE_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CruiseResponse> create(@RequestBody @Valid CruiseCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách chuyến đi", description = "Lấy danh sách các chuyến đi (hỗ trợ phân trang và lọc theo trạng thái, tàu, loại chuyến)")
    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<Page<CruiseResponse>> list(
            @Parameter(description = "Lọc theo trạng thái chuyến đi") @RequestParam(required = false) CruiseStatus status,
            @Parameter(description = "Lọc theo ID tàu") @RequestParam(required = false) UUID shipId,
            @Parameter(description = "Lọc theo loại chuyến đi (ONE_DAY, MULTI_DAYS)") @RequestParam(required = false) CruiseCruiseType cruiseType,
            Pageable pageable,
            org.springframework.security.core.Authentication auth) {
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        CruiseStatus effectiveStatus = hasCruiseRead ? status : CruiseStatus.PUBLISHED;
        return ApiResponse.success(service.list(effectiveStatus, shipId, cruiseType, pageable));
    }

    @Operation(summary = "Chi tiết chuyến đi", description = "Lấy thông tin chi tiết của một chuyến đi theo ID")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}")
    public ApiResponse<CruiseResponse> detail(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            org.springframework.security.core.Authentication auth) {
        CruiseResponse response = service.detail(cruiseId);
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        if (!hasCruiseRead && response.getStatus() != CruiseStatus.PUBLISHED) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to non-published cruise");
        }
        return ApiResponse.success(response);
    }

    @Operation(summary = "Cập nhật bản nháp chuyến đi", description = "Cập nhật thông tin chuyến đi (chỉ áp dụng khi chuyến ở trạng thái DRAFT)")
    @PreAuthorize("hasAuthority('CRUISE_UPDATE_DRAFT')")
    @PatchMapping("/{cruiseId}/draft")
    public ApiResponse<CruiseResponse> updateDraft(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseUpdateDraftRequest request) {
        return ApiResponse.success(service.updateDraft(cruiseId, request));
    }

    @Operation(summary = "Xóa bản nháp chuyến đi", description = "Xóa chuyến đi ở trạng thái DRAFT và chưa có đặt chỗ nào")
    @PreAuthorize("hasAuthority('CRUISE_DELETE_DRAFT')")
    @DeleteMapping("/{cruiseId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        service.deleteDraft(cruiseId);
        return ApiResponse.success("Deleted draft cruise successfully", null);
    }

    @Operation(summary = "Gán tàu cho chuyến đi", description = "Gán tàu đang ACTIVE và không bị trùng lịch cho chuyến đi (khi chuyến ở trạng thái DRAFT hoặc PUBLISHED)")
    @PreAuthorize("hasAuthority('CRUISE_ASSIGN_SHIP')")
    @PostMapping("/{cruiseId}/assign-ship")
    public ApiResponse<CruiseResponse> assignShip(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu cần gán", required = true) @RequestParam UUID shipId) {
        return ApiResponse.success(service.assignShip(cruiseId, shipId));
    }

    @Operation(summary = "Thay đổi tàu đã gán", description = "Đổi sang tàu khác cho chuyến đi (kiểm tra tàu mới ACTIVE, không trùng lịch, và sức chứa đáp ứng lượng khách đã đặt)")
    @PreAuthorize("hasAuthority('CRUISE_CHANGE_SHIP')")
    @PutMapping("/{cruiseId}/change-assigned-ship")
    public ApiResponse<CruiseResponse> changeAssignedShip(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu mới", required = true) @RequestParam UUID newShipId) {
        return ApiResponse.success(service.changeAssignedShip(cruiseId, newShipId));
    }

    @Operation(summary = "Hủy gán tàu khỏi chuyến đi", description = "Gỡ bỏ tàu khỏi chuyến đi (chỉ áp dụng khi chuyến ở trạng thái DRAFT và chưa có đặt chỗ)")
    @PreAuthorize("hasAuthority('CRUISE_UNASSIGN_SHIP')")
    @PostMapping("/{cruiseId}/unassign-ship")
    public ApiResponse<CruiseResponse> unassignShip(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.unassignShip(cruiseId));
    }

    @Operation(summary = "Kiểm tra tính khả dụng của tàu đối với chuyến đi", description = "Kiểm tra tàu có ACTIVE và không bị trùng lịch với chuyến đi này không")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-ship-availability")
    public ApiResponse<Boolean> validateShipAvailability(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu cần kiểm tra", required = true) @RequestParam UUID shipId) {
        return ApiResponse.success(service.validateShipAvailability(cruiseId, shipId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của lịch trình chuyến đi", description = "Xác nhận thời điểm trở về sau thời điểm khởi hành")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-schedule")
    public ApiResponse<Boolean> validateSchedule(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateSchedule(cruiseId));
    }

    @Operation(summary = "Kiểm tra sức chứa của chuyến đi", description = "So sánh sức chứa tối đa của tàu với số lượng hành khách đã đặt chỗ hiện tại")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-capacity")
    public ApiResponse<CruiseCapacityValidationResponse> validateCapacity(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateCapacity(cruiseId));
    }

    @Operation(summary = "Kiểm tra tính sẵn sàng vận hành (Operational Readiness)", description = "Đánh giá toàn diện: tàu đã gán và ACTIVE, có tầng và phòng, lịch trình hợp lệ, cảng hoạt động, hải trình đã cấu hình")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/readiness")
    public ApiResponse<CruiseReadinessResponse> validateOperationalReadiness(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateOperationalReadiness(cruiseId));
    }

    @Operation(summary = "Phát hành chuyến đi (Publish)", description = "Chuyển trạng thái từ DRAFT sang PUBLISHED (yêu cầu kiểm tra sẵn sàng vận hành đạt tiêu chuẩn)")
    @PreAuthorize("hasAuthority('CRUISE_PUBLISH')")
    @PostMapping("/{cruiseId}/publish")
    public ApiResponse<CruiseResponse> publish(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.publish(cruiseId));
    }

    @Operation(summary = "Bắt đầu chuyến đi (Start)", description = "Chuyển trạng thái từ PUBLISHED sang IN_PROGRESS khi chuyến đi chính thức bắt đầu")
    @PreAuthorize("hasAuthority('CRUISE_START')")
    @PostMapping("/{cruiseId}/start")
    public ApiResponse<CruiseResponse> start(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.start(cruiseId));
    }

    @Operation(summary = "Hoàn tất chuyến đi (Complete)", description = "Chuyển trạng thái từ IN_PROGRESS sang COMPLETED khi tàu cập cảng an toàn và kết thúc chuyến đi")
    @PreAuthorize("hasAuthority('CRUISE_COMPLETE')")
    @PostMapping("/{cruiseId}/complete")
    public ApiResponse<CruiseResponse> complete(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.complete(cruiseId));
    }

    @Operation(summary = "Hủy chuyến đi (Cancel)", description = "Hủy chuyến đi (yêu cầu cung cấp lý do hủy, không thể hủy chuyến đã hoàn tất)")
    @PreAuthorize("hasAuthority('CRUISE_CANCEL')")
    @PostMapping("/{cruiseId}/cancel")
    public ApiResponse<CruiseResponse> cancel(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseCancelRequest request) {
        return ApiResponse.success(service.cancel(cruiseId, request.getReason()));
    }

    // ==================== CRUISE STAFF ASSIGNMENT ====================

    @Operation(summary = "Phân công nhân viên / thủy thủ đoàn vào chuyến đi", description = "Gán nhân viên vào chuyến đi, kiểm tra nhân viên active và không bị trùng lịch tàu khác")
    @PreAuthorize("hasAuthority('CRUISE_STAFF_ASSIGN')")
    @PostMapping("/{cruiseId}/staff")
    public ApiResponse<CruiseStaffResponse> assignStaff(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseStaffRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success("Assigned staff to cruise successfully",
                staffService.assignStaff(cruiseId, request, principal.getUserId()));
    }

    @Operation(summary = "Danh sách nhân viên / thủy thủ đoàn trên chuyến đi", description = "Lấy danh sách nhân sự được phân công phục vụ chuyến đi")
    @PreAuthorize("hasAuthority('CRUISE_STAFF_READ')")
    @GetMapping("/{cruiseId}/staff")
    public ApiResponse<List<CruiseStaffResponse>> listStaff(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(staffService.listStaff(cruiseId));
    }

    @Operation(summary = "Hủy phân công nhân viên khỏi chuyến đi", description = "Gỡ bỏ nhân sự khỏi chuyến đi")
    @PreAuthorize("hasAuthority('CRUISE_STAFF_REMOVE')")
    @DeleteMapping("/{cruiseId}/staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeStaff(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID người dùng", required = true) @PathVariable UUID userId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        staffService.removeStaff(cruiseId, userId, principal.getUserId());
        return ApiResponse.success("Staff member relieved from cruise", null);
    }

    // ==================== CRUISE TOUR PACKAGES ====================

    @Operation(summary = "Gán gói dịch vụ du lịch (Tour Package) vào chuyến đi", description = "Mở bán gói dịch vụ cho du khách tham gia chuyến đi này")
    @PreAuthorize("hasAuthority('TOUR_PACKAGE_ASSIGN')")
    @PostMapping("/{cruiseId}/packages")
    public ApiResponse<CruiseTourPackageResponse> assignPackage(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseTourPackageRequest request) {
        return ApiResponse.success("Assigned package to cruise successfully", packageService.assignPackage(cruiseId, request));
    }

    @Operation(summary = "Danh sách gói dịch vụ mở bán trên chuyến đi", description = "Lấy danh sách các tour package đang được kích hoạt cho chuyến đi")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}/packages")
    public ApiResponse<List<CruiseTourPackageResponse>> listPackages(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(packageService.listPackages(cruiseId));
    }

    @Operation(summary = "Gỡ gói dịch vụ khỏi chuyến đi", description = "Ngừng mở bán gói dịch vụ trên chuyến đi")
    @PreAuthorize("hasAuthority('TOUR_PACKAGE_ASSIGN')")
    @DeleteMapping("/{cruiseId}/packages/{packageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removePackage(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID gói dịch vụ", required = true) @PathVariable UUID packageId) {
        packageService.removePackage(cruiseId, packageId);
        return ApiResponse.success("Tour package removed from cruise", null);
    }

    // ==================== CRUISE MANIFEST ====================

    @Operation(summary = "Xuất báo cáo danh sách hành khách và thuyền viên (Cruise Manifest)", description = "Cung cấp danh sách hành khách đã đặt phòng, thủy thủ đoàn và thông số tải trọng cho cơ quan cảng vụ")
    @PreAuthorize("hasAuthority('CRUISE_MANIFEST_READ')")
    @GetMapping("/{cruiseId}/manifest")
    public ApiResponse<CruiseManifestResponse> getManifest(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        CruiseManifestResponse manifest = service.getManifest(cruiseId);
        // Include staff list from staffService
        List<CruiseStaffResponse> staff = staffService.listStaff(cruiseId);
        CruiseManifestResponse finalManifest = CruiseManifestResponse.builder()
                .cruiseId(manifest.getCruiseId())
                .cruiseCode(manifest.getCruiseCode())
                .cruiseName(manifest.getCruiseName())
                .shipName(manifest.getShipName())
                .totalCapacity(manifest.getTotalCapacity())
                .bookedPassengers(manifest.getBookedPassengers())
                .departureTime(manifest.getDepartureTime())
                .returnTime(manifest.getReturnTime())
                .originPort(manifest.getOriginPort())
                .destinationPort(manifest.getDestinationPort())
                .staffList(staff)
                .passengerList(manifest.getPassengerList())
                .build();
        return ApiResponse.success(finalManifest);
    }
}
