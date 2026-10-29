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
import com.casms.ship.dto.response.CruiseSellabilityResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.CruiseTourPackageResponse;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.dto.response.CruiseChangeImpactResponse;
import com.casms.ship.dto.response.CruiseImpactTaskResponse;
import com.casms.ship.dto.response.CruiseApplyChangeResponse;
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
import com.casms.notification.service.NotificationService;
import com.casms.notification.dto.ImpactNotificationAggregateResponse;
import com.casms.ship.service.CruiseAuditService;
import com.casms.ship.dto.response.CruiseAuditEventResponse;
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
@RequestMapping({"/cruise", "/cruises"})
@RequiredArgsConstructor
public class CruiseController {

    private final CruiseService service;
    private final CruiseStaffService staffService;
    private final CruisePackageService packageService;
    private final IdempotencyService idempotencyService;
    private final ScheduleMaterializationService scheduleMaterializationService;
    private final OperationalProgramMappingService operationalProgramMappingService;
    private final CruiseImpactAssessmentService cruiseImpactAssessmentService;
    private final NotificationService notificationService;
    private final CruiseAuditService cruiseAuditService;

    @Operation(summary = "Xem trước lịch trình chuyến đi (Preview)", description = "Tính toán ngày thực tế cho các ngày và điểm dừng từ phiên bản TripScheduleVersion đã phát hành mà không lưu vào DB (C02)")
    @PreAuthorize("hasAnyAuthority('CRUISE_CREATE', 'CRUISE_READ', 'TRIP_READ')")
    @PostMapping("/preview-schedule")
    public ApiResponse<CruiseSchedulePreviewResponse> previewSchedule(
            @RequestBody @Valid CruiseSchedulePreviewRequest request) {
        return ApiResponse.success(scheduleMaterializationService.previewSchedule(
                request.getSourceVersionId(), request.getDepartureDatetime()));
    }

    @Operation(summary = "Xem lịch trình thực tế của chuyến đi", description = "Đọc lại lịch trình (Itinerary, Days, CruiseStops) đã được tạo cho chuyến đi (C04)")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'TRIP_READ', 'ITINERARY_READ')")
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
            @Parameter(description = "Lọc các chuyến đi chưa gán tàu (unassigned=true)") @RequestParam(required = false) Boolean unassigned,
            @Parameter(description = "Lọc theo loại chuyến đi (ONE_DAY, MULTI_DAYS)") @RequestParam(required = false) CruiseCruiseType cruiseType,
            @Parameter(description = "Lọc theo ID Trip mẫu nguồn (C15)") @RequestParam(required = false) UUID sourceTripId,
            @Parameter(description = "Lọc theo ID phiên bản TripScheduleVersion nguồn (C15)") @RequestParam(required = false) UUID sourceVersionId,
            @RequestParam(required = false) String saleState,
            Pageable pageable,
            org.springframework.security.core.Authentication auth) {
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        validateSaleState(saleState);
        boolean bookingView = !hasCruiseRead || bookingOnly(auth) || saleState != null;
        Page<CruiseResponse> page = bookingView
                ? service.listOpenForSale(shipId, unassigned, cruiseType, sourceTripId, sourceVersionId, pageable)
                : service.list(status, shipId, unassigned, cruiseType, sourceTripId, sourceVersionId, pageable);
        if (!hasCruiseRead) {
            page = page.map(this::sanitizeForPublic);
        }
        return ApiResponse.success(page);
    }

    @Operation(summary = "Chi tiết chuyến đi", description = "Lấy thông tin chi tiết của một chuyến đi theo ID")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}")
    public ApiResponse<CruiseResponse> detail(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestParam(required = false) String saleState,
            org.springframework.security.core.Authentication auth) {
        validateSaleState(saleState);
        CruiseResponse response = service.detail(cruiseId);
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        if ((!hasCruiseRead || bookingOnly(auth) || saleState != null)
                && (response.getStatus() != CruiseStatus.PUBLISHED
                || !"OPEN_FOR_SALE".equals(service.getPublicSellability(cruiseId).getSaleState()))) {
            throw new org.springframework.security.access.AccessDeniedException("Lần khởi hành chưa mở bán");
        }
        if (!hasCruiseRead) {
            response = sanitizeForPublic(response);
        }
        return ApiResponse.success(response);
    }

    private void validateSaleState(String saleState) {
        if (saleState != null && !"OPEN_FOR_SALE".equals(saleState))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "saleState must be OPEN_FOR_SALE");
    }

    private boolean bookingOnly(org.springframework.security.core.Authentication auth) {
        if (auth == null) return false;
        var authorities = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        return authorities.contains("ROLE_BOOKING_AGENT") && java.util.Collections.disjoint(authorities,
                List.of("ROLE_ADMIN", "ROLE_CRUISE_MANAGER", "ROLE_OPERATIONS_MANAGER", "ROLE_SHIP_MANAGER", "ROLE_ACTIVITY_SERVICE_MANAGER"));
    }

    private CruiseResponse sanitizeForPublic(CruiseResponse res) {
        if (res == null) return null;
        res.setStoppedByUsername(null);
        res.setStopEvidence(null);
        res.setStopEventIdentity(null);
        res.setOpenedForSaleByUsername(null);
        res.setSaleEventIdentity(null);
        res.setDepartureActorUsername(null);
        res.setCompletionActorUsername(null);
        res.setClosureNotes(null);
        res.setRevision(null);
        res.setSourceVersionRevision(null);
        return res;
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
            @Parameter(description = "ID tàu cần gán", required = true) @RequestParam UUID shipId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.assignShip(cruiseId, shipId, actorId));
    }

    @Operation(summary = "Thay đổi tàu đã gán", description = "Đổi sang tàu khác cho chuyến đi (kiểm tra tàu mới ACTIVE, không trùng lịch, và sức chứa đáp ứng lượng khách đã đặt)")
    @PreAuthorize("hasAuthority('CRUISE_CHANGE_SHIP')")
    @PutMapping("/{cruiseId}/change-assigned-ship")
    public ApiResponse<CruiseResponse> changeAssignedShip(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu mới", required = true) @RequestParam UUID newShipId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.changeAssignedShip(cruiseId, newShipId, actorId));
    }

    @Operation(summary = "Hủy gán tàu khỏi chuyến đi", description = "Gỡ bỏ tàu khỏi chuyến đi (chỉ áp dụng khi chuyến ở trạng thái DRAFT và chưa có đặt chỗ)")
    @PreAuthorize("hasAuthority('CRUISE_UNASSIGN_SHIP')")
    @PostMapping("/{cruiseId}/unassign-ship")
    public ApiResponse<CruiseResponse> unassignShip(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.unassignShip(cruiseId, actorId));
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

    @Deprecated
    @Operation(summary = "Phát hành chuyến đi (Publish - Legacy / Operational materialization)",
               description = "Chuyển trạng thái từ DRAFT sang PUBLISHED để ghi nhận chuyến đi đã sẵn sàng vận hành. LƯU Ý: Thao tác này KHÔNG mở bán thương mại cho hành khách (khách hàng chỉ có thể mua vé sau khi OPS mở bán qua POST /{cruiseId}/open-sale).")
    @PreAuthorize("hasAuthority('CRUISE_PUBLISH')")
    @PostMapping("/{cruiseId}/publish")
    public ApiResponse<CruiseResponse> publish(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.publish(cruiseId));
    }

    @Operation(summary = "Bắt đầu chuyến đi (Start - BE-21)", description = "Chuyển trạng thái từ PUBLISHED sang IN_PROGRESS khi chuyến đi chính thức bắt đầu (bắt buộc kiểm tra Departure Readiness)")
    @PreAuthorize("hasAuthority('CRUISE_START')")
    @PostMapping("/{cruiseId}/start")
    public ApiResponse<CruiseResponse> start(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.start(cruiseId, actorId));
    }

    @Operation(summary = "Hoàn tất chuyến đi (Complete - BE-21)", description = "Chuyển trạng thái từ IN_PROGRESS sang COMPLETED khi tàu cập cảng an toàn và kết thúc chuyến đi (kiểm tra checklist đóng chuyến)")
    @PreAuthorize("hasAuthority('CRUISE_COMPLETE')")
    @PostMapping("/{cruiseId}/complete")
    public ApiResponse<CruiseResponse> complete(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Ghi chú đóng chuyến và quyết toán", required = false) @RequestParam(required = false) String closureNotes,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.complete(cruiseId, actorId, closureNotes));
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
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
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
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
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
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE', 'CRUISE_UPDATE_DRAFT')")
    @PostMapping(value = {"/{cruiseId}/preview-change-impact", "/{cruiseId}/change-impact"})
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

    @Operation(summary = "Đánh dấu hoàn tất công việc xử lý tác động (C17)", description = "Cập nhật trạng thái công việc thành RESOLVED kèm ghi chú giải quyết và bằng chứng")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_RESOLVE')")
    @PostMapping("/{cruiseId}/impact-tasks/{taskId}/resolve")
    public ApiResponse<CruiseImpactTaskResponse> resolveImpactTask(
            @PathVariable UUID cruiseId,
            @PathVariable UUID taskId,
            @RequestParam String resolutionNotes,
            @RequestParam(required = false) Long expectedRevision,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(cruiseImpactAssessmentService.resolveTask(cruiseId, taskId, resolutionNotes, expectedRevision, actorId));
    }

    @Operation(summary = "Áp dụng thay đổi lịch trình, tàu hoặc cấu hình (BE-R05b / C11, C17)",
               description = "Áp dụng thay đổi toàn diện với kiểm tra dữ liệu mới nhất, bảo vệ booking hiện có và đồng bộ các đối tượng phụ thuộc")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE')")
    @PostMapping("/{cruiseId}/apply-change")
    public ApiResponse<CruiseApplyChangeResponse> applyChange(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseChangeImpactRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.applyChange(cruiseId, request, actorId));
    }

    @Operation(summary = "Thống kê an toàn kết quả phát thông báo tác động (BE-19)", description = "Cung cấp safe aggregate status cho FE/OPS, không để lộ PII qua dashboard chung")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'OPERATIONAL_ISSUE_READ')")
    @GetMapping("/{cruiseId}/impact-notifications/aggregate")
    public ApiResponse<ImpactNotificationAggregateResponse> getImpactNotificationAggregate(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(notificationService.getImpactAggregate(cruiseId));
    }

    @Operation(summary = "Phát thông báo tác động an toàn và cập nhật trạng thái tác vụ (BE-19)", description = "Ghi outbox đáng tin cậy, retry idempotent, tách biệt task created với customer notified")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_RESOLVE')")
    @PostMapping("/{cruiseId}/impact-notifications/dispatch")
    public ApiResponse<ImpactNotificationAggregateResponse> dispatchImpactNotifications(
            @PathVariable UUID cruiseId,
            @RequestParam(required = false) String eventIdentity,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(notificationService.dispatchImpactNotifications(cruiseId, eventIdentity, actorId));
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
            @PathVariable UUID cruiseId, @RequestParam(defaultValue = "WHOLE_CRUISE") String saleScope,
            @RequestParam(required = false) java.util.Set<UUID> selectedPackageIds) {
        return ApiResponse.success(service.validateSaleReadiness(cruiseId, saleScope, selectedPackageIds));
    }

    @Operation(summary = "Đánh giá điều kiện khởi hành của chuyến đi (Departure Readiness Gate - BE-08)", description = "Kiểm tra toàn diện điều kiện khởi hành bao gồm thuyền trưởng, nhân sự, và 5 phân hệ chuẩn bị liên phòng ban")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/departure-readiness")
    public ApiResponse<CruiseReadinessResponse> getDepartureReadiness(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateDepartureReadiness(cruiseId));
    }

    @Operation(summary = "Mở bán chuyến đi hoặc sản phẩm dịch vụ (BE-08B / BE-13 / BE-H04)", description = "Kích hoạt trạng thái PUBLISHED cho chuyến đi và OPEN_FOR_SALE cho lịch trình sau khi đạt kiểm tra sale readiness theo scope")
    @PreAuthorize("hasAuthority('CRUISE_OPEN_SALE')")
    @PostMapping("/{cruiseId}/open-sale")
    public ApiResponse<CruiseResponse> openSale(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid @jakarta.validation.constraints.NotNull CruiseOpenSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.openSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Kích hoạt gói dịch vụ để mở bán (BE-12)", description = "Chuyển trạng thái gói dịch vụ từ cấu hình danh mục sang sẵn sàng bán")
    @PreAuthorize("hasAuthority('CRUISE_OPEN_SALE')")
    @PostMapping("/{cruiseId}/packages/{tourPackageId}/activate")
    public ApiResponse<CruiseTourPackageResponse> activatePackage(
            @PathVariable UUID cruiseId,
            @PathVariable UUID tourPackageId) {
        return ApiResponse.success(packageService.activatePackage(cruiseId, tourPackageId));
    }

    @Operation(summary = "Hủy kích hoạt bán gói dịch vụ (BE-12)", description = "Đưa gói dịch vụ về lại trạng thái cấu hình mà không xóa khỏi chuyến đi")
    @PreAuthorize("hasAuthority('CRUISE_STOP_SALE')")
    @PostMapping("/{cruiseId}/packages/{tourPackageId}/deactivate")
    public ApiResponse<CruiseTourPackageResponse> deactivatePackage(
            @PathVariable UUID cruiseId,
            @PathVariable UUID tourPackageId) {
        return ApiResponse.success(packageService.deactivatePackage(cruiseId, tourPackageId));
    }

    @Operation(summary = "Tạm dừng mở bán chuyến đi hoặc gói dịch vụ cụ thể (BE-08B / C16)", description = "Tạm dừng nhận booking mới khi điều kiện vận hành bị vi phạm mà không làm hủy các booking hiện có")
    @PreAuthorize("hasAuthority('CRUISE_STOP_SALE')")
    @PostMapping("/{cruiseId}/stop-sale")
    public ApiResponse<CruiseResponse> stopSale(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseStopSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.stopSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Tiếp tục mở bán sau khi tạm dừng (BE-08B / C16)", description = "Mở lại bán cho toàn bộ chuyến đi hoặc gói dịch vụ sau khi kiểm tra lại sale readiness")
    @PreAuthorize("hasAuthority('CRUISE_RESUME_SALE')")
    @PostMapping("/{cruiseId}/resume-sale")
    public ApiResponse<CruiseResponse> resumeSale(
            @PathVariable UUID cruiseId,
            @RequestBody(required = false) CruiseResumeSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.resumeSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Truy vấn tình trạng mở bán công khai authoritative (BE-15 Public Sellability)",
               description = "Cung cấp nguồn sự thật duy nhất (Single Source of Truth) về trạng thái mở bán của chuyến đi, từng hạng phòng và gói tour kèm theo mà không để lộ dữ liệu nhân sự, manifest hay nhà cung cấp.")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}/sellability")
    public ApiResponse<CruiseSellabilityResponse> getPublicSellability(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getPublicSellability(cruiseId));
    }

    @Operation(summary = "Bàn giao dữ liệu và thông tin manifest cho hệ thống điều hành (BE-09 / C14)", description = "Trích xuất context chuyến đi, danh sách manifest phân biệt rõ đặt chỗ, check-in và lên tàu cho bộ phận cảng")
    @PreAuthorize("hasAuthority('CRUISE_MANIFEST_READ')")
    @GetMapping("/{cruiseId}/downstream-handoff")
    public ApiResponse<CruiseDownstreamHandoffResponse> getDownstreamHandoff(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getDownstreamHandoff(cruiseId));
    }

    @Operation(summary = "Truy nguyên dòng thời gian quyết định và sự kiện kiểm toán Cruise (BE-25)",
               description = "Lấy lịch sử sự kiện kiểm toán (audit trail) đã được chuẩn hóa, che chắn dữ liệu nhạy cảm theo actor, scope, revision.")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'OPERATIONAL_ISSUE_READ')")
    @GetMapping("/{cruiseId}/audit-events")
    public ApiResponse<List<CruiseAuditEventResponse>> getAuditEvents(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Lọc theo loại sự kiện", required = false) @RequestParam(required = false) String eventType) {
        return ApiResponse.success(cruiseAuditService.listAuditEvents(cruiseId, eventType));
    }
}
