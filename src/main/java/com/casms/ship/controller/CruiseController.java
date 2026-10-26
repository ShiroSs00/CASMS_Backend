package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.CruiseCancelRequest;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.request.CruiseTourPackageRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.request.CruiseOpenSaleRequest;
import com.casms.ship.dto.request.CruiseStopSaleRequest;
import com.casms.ship.dto.request.CruiseResumeSaleRequest;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseDownstreamHandoffResponse;
import com.casms.ship.dto.response.CruiseInventorySummaryResponse;
import com.casms.ship.dto.response.CruiseManifestResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSaleReadinessResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.CruiseTourPackageResponse;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.dto.response.CruiseChangeImpactResponse;
import com.casms.ship.dto.response.CruiseImpactTaskResponse;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.dto.request.CruiseSchedulePreviewRequest;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.service.ScheduleMaterializationService;
import com.casms.ship.service.CruisePackageService;
import com.casms.ship.service.CruiseService;
import com.casms.ship.service.CruiseStaffService;
import com.casms.ship.service.OperationalProgramMappingService;
import com.casms.ship.service.CruiseImpactAssessmentService;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.service.IdempotencyService;
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
    private final IdempotencyService idempotencyService;
    private final ScheduleMaterializationService scheduleMaterializationService;
    private final OperationalProgramMappingService operationalProgramMappingService;
    private final CruiseImpactAssessmentService cruiseImpactAssessmentService;

    @Operation(summary = "Xem trước lịch trình chuyến đi (Preview)", description = "Tính toán ngày thực tế cho các ngày và điểm dừng từ phiên bản TripScheduleVersion đã phát hành mà không lưu vào DB (C02)")
    @PreAuthorize("hasAnyAuthority('CRUISE_CREATE', 'CRUISE_READ', 'TRIP_READ')")
    @PostMapping("/preview-schedule")
    public ApiResponse<CruiseSchedulePreviewResponse> previewSchedule(
            @RequestBody @Valid CruiseSchedulePreviewRequest request) {
        return ApiResponse.success(scheduleMaterializationService.previewSchedule(
                request.getSourceVersionId(), request.getDepartureDatetime()));
    }

    @Operation(summary = "Xem lịch trình thực tế của chuyến đi", description = "Đọc lại lịch trình (Itinerary, Days, CruiseStops) đã được tạo cho chuyến đi (C04)")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}/schedule")
    public ApiResponse<CruiseSchedulePreviewResponse> getCruiseSchedule(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(scheduleMaterializationService.getCruiseSchedule(cruiseId));
    }

    @Operation(summary = "Tạo chuyến đi mới", description = "Khởi tạo bản ghi chuyến đi (mặc định ở trạng thái DRAFT). Có thể gán tàu ngay hoặc gán sau. Yêu cầu Idempotency-Key header.")
    @PreAuthorize("hasAuthority('CRUISE_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CruiseResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CruiseCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Header 'Idempotency-Key' là bắt buộc khi tạo Cruise");
        }

        UUID actorId = principal != null ? principal.getUserId() : null;
        CruiseResponse response = idempotencyService.execute(
                idempotencyKey, actorId, "CREATE_CRUISE", request, CruiseResponse.class,
                () -> service.create(request));
        return ApiResponse.success("Created Cruise successfully", response);
    }

    @Operation(summary = "Danh sách chuyến đi", description = "Lấy danh sách các chuyến đi (hỗ trợ phân trang và lọc theo trạng thái, tàu, loại chuyến, sourceTripId, sourceVersionId)")
    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<Page<CruiseResponse>> list(
            @Parameter(description = "Lọc theo trạng thái chuyến đi") @RequestParam(required = false) CruiseStatus status,
            @Parameter(description = "Lọc theo ID tàu") @RequestParam(required = false) UUID shipId,
            @Parameter(description = "Lọc theo loại chuyến đi (ONE_DAY, MULTI_DAYS)") @RequestParam(required = false) CruiseCruiseType cruiseType,
            @Parameter(description = "Lọc theo ID Trip mẫu nguồn (C15)") @RequestParam(required = false) UUID sourceTripId,
            @Parameter(description = "Lọc theo ID phiên bản TripScheduleVersion nguồn (C15)") @RequestParam(required = false) UUID sourceVersionId,
            Pageable pageable,
            org.springframework.security.core.Authentication auth) {
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        CruiseStatus effectiveStatus = hasCruiseRead ? status : CruiseStatus.PUBLISHED;
        return ApiResponse.success(service.list(effectiveStatus, shipId, cruiseType, sourceTripId, sourceVersionId, pageable));
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

    @Operation(summary = "Kiểm tra tính tương thích và khả dụng của tàu (C05)", description = "Đánh giá chi tiết trạng thái, cấu hình boong/phòng, lịch trống và sức chứa của tàu đối với chuyến đi")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_ASSIGN_SHIP', 'CRUISE_CHANGE_SHIP', 'SHIP_READ')")
    @GetMapping("/{cruiseId}/ship-compatibility")
    public ApiResponse<ShipCompatibilityResponse> checkShipCompatibility(
            @PathVariable UUID cruiseId,
            @RequestParam UUID shipId) {
        return ApiResponse.success(service.checkShipCompatibility(cruiseId, shipId));
    }

    @Operation(summary = "Xem trước tác động khi đổi tàu (C05, C11)", description = "Đánh giá ảnh hưởng đến sức chứa, phân bổ phòng và các hoạt động trên tàu khi đổi sang tàu khác")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_CHANGE_SHIP', 'SHIP_READ')")
    @GetMapping("/{cruiseId}/preview-ship-change")
    public ApiResponse<ShipChangeImpactResponse> previewShipChangeImpact(
            @PathVariable UUID cruiseId,
            @RequestParam UUID newShipId) {
        return ApiResponse.success(service.previewShipChangeImpact(cruiseId, newShipId));
    }

    @Operation(summary = "Đồng bộ chương trình vận hành từ Trip Plan (C06)", description = "Ánh xạ các entry từ TripScheduleVersion sang Onboard Activities và Shore Excursions cho Cruise")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'ACTIVITY_UPDATE', 'EXCURSION_UPDATE')")
    @PostMapping("/{cruiseId}/sync-operational-programs")
    public ApiResponse<OperationalProgramSyncResponse> syncOperationalPrograms(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(operationalProgramMappingService.syncProgramsForCruise(cruiseId));
    }

    @Operation(summary = "Xem trước tác động thay đổi lịch trình hoặc cấu hình (C11, C17)", description = "Đánh giá tác động dời ngày, đổi tàu đối với khách đặt, tour, hoạt động và sinh ra danh sách công việc xử lý")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE')")
    @PostMapping("/{cruiseId}/preview-change-impact")
    public ApiResponse<CruiseChangeImpactResponse> previewChangeImpact(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseChangeImpactRequest request) {
        return ApiResponse.success(cruiseImpactAssessmentService.previewChangeImpact(cruiseId, request));
    }

    @Operation(summary = "Khởi tạo công việc xử lý tác động thay đổi (C17)", description = "Lưu lại các công việc xử lý tác động thay đổi chuyến đi vào cơ sở dữ liệu")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_CREATE')")
    @PostMapping("/{cruiseId}/impact-tasks/generate")
    public ApiResponse<List<CruiseImpactTaskResponse>> generateImpactTasks(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseChangeImpactRequest request) {
        return ApiResponse.success(cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruiseId, request));
    }

    @Operation(summary = "Danh sách công việc xử lý tác động (C17)", description = "Tra cứu các công việc xử lý tác động phát sinh khi chuyến đi bị thay đổi")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'OPERATIONAL_ISSUE_READ')")
    @GetMapping("/{cruiseId}/impact-tasks")
    public ApiResponse<List<CruiseImpactTaskResponse>> listImpactTasks(
            @PathVariable UUID cruiseId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(cruiseImpactAssessmentService.listTasks(cruiseId, status));
    }

    @Operation(summary = "Đánh dấu hoàn tất công việc xử lý tác động (C17)", description = "Cập nhật trạng thái công việc thành RESOLVED kèm ghi chú giải quyết")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_RESOLVE')")
    @PostMapping("/{cruiseId}/impact-tasks/{taskId}/resolve")
    public ApiResponse<CruiseImpactTaskResponse> resolveImpactTask(
            @PathVariable UUID cruiseId,
            @PathVariable UUID taskId,
            @RequestParam(required = false, defaultValue = "Resolved by operations staff") String resolutionNotes,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(cruiseImpactAssessmentService.resolveTask(taskId, resolutionNotes, actorId));
    }

    @Operation(summary = "Lấy báo cáo tồn chỗ và năng lực phục vụ của chuyến đi (BE-07 / C08)", description = "Tra cứu chi tiết tồn phòng theo từng hạng và tổng sức chứa khả dụng phục vụ mở bán")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'BOOKING_READ')")
    @GetMapping("/{cruiseId}/inventory-summary")
    public ApiResponse<CruiseInventorySummaryResponse> getInventorySummary(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getInventorySummary(cruiseId));
    }

    @Operation(summary = "Đánh giá điều kiện mở bán của chuyến đi (BE-08A / C09)", description = "Kiểm tra toàn diện điều kiện mở bán bao gồm vận hành, lịch trình, sản phẩm/gói dịch vụ, tồn chỗ và giá")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/sale-readiness")
    public ApiResponse<CruiseSaleReadinessResponse> getSaleReadiness(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateSaleReadiness(cruiseId));
    }

    @Operation(summary = "Mở bán chuyến đi hoặc sản phẩm dịch vụ (BE-08B / C10)", description = "Kích hoạt trạng thái PUBLISHED cho chuyến đi và OPEN_FOR_SALE cho lịch trình sau khi đạt kiểm tra sale readiness")
    @PreAuthorize("hasAnyAuthority('CRUISE_PUBLISH', 'CRUISE_STATUS_CHANGE')")
    @PostMapping("/{cruiseId}/open-sale")
    public ApiResponse<CruiseResponse> openSale(
            @PathVariable UUID cruiseId,
            @RequestBody(required = false) CruiseOpenSaleRequest request) {
        return ApiResponse.success(service.openSale(cruiseId, request));
    }

    @Operation(summary = "Tạm dừng mở bán chuyến đi hoặc gói dịch vụ cụ thể (BE-08B / C16)", description = "Tạm dừng nhận booking mới khi điều kiện vận hành bị vi phạm mà không làm hủy các booking hiện có")
    @PreAuthorize("hasAnyAuthority('CRUISE_PUBLISH', 'CRUISE_STATUS_CHANGE')")
    @PostMapping("/{cruiseId}/stop-sale")
    public ApiResponse<CruiseResponse> stopSale(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseStopSaleRequest request) {
        return ApiResponse.success(service.stopSale(cruiseId, request));
    }

    @Operation(summary = "Tiếp tục mở bán sau khi tạm dừng (BE-08B / C16)", description = "Mở lại bán cho toàn bộ chuyến đi hoặc gói dịch vụ sau khi kiểm tra lại sale readiness")
    @PreAuthorize("hasAnyAuthority('CRUISE_PUBLISH', 'CRUISE_STATUS_CHANGE')")
    @PostMapping("/{cruiseId}/resume-sale")
    public ApiResponse<CruiseResponse> resumeSale(
            @PathVariable UUID cruiseId,
            @RequestBody(required = false) CruiseResumeSaleRequest request) {
        return ApiResponse.success(service.resumeSale(cruiseId, request));
    }

    @Operation(summary = "Bàn giao dữ liệu và thông tin manifest cho hệ thống điều hành (BE-09 / C14)", description = "Trích xuất context chuyến đi, danh sách manifest phân biệt rõ đặt chỗ, check-in và lên tàu cho bộ phận cảng")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CHECKIN_READ')")
    @GetMapping("/{cruiseId}/downstream-handoff")
    public ApiResponse<CruiseDownstreamHandoffResponse> getDownstreamHandoff(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getDownstreamHandoff(cruiseId));
    }
}
