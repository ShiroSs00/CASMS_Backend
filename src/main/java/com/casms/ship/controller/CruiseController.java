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

@Tag(name = "Cruise Management", description = "Quản lý kế hoạch và vòng đời chuyến khởi hành du thuyền (Cruise)")
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

    @Operation(summary = "Xem trước lịch trình chuyến khởi hành (Preview)", description = "Tính toán ngày thực tế cho các ngày và điểm dừng từ phiên bản TripScheduleVersion đã phát hành mà không lưu vào DB (C02)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VERSION_NOT_PUBLISHED / TRIP_ARCHIVED / SOURCE_DURATION_INVALID: nguồn không hợp lệ. Preview thành công vẫn có thể trả portCallWindowStatus=BLOCK với portWindowChecks và materializationBlockers; Create luôn đánh giá lại bằng evaluator hiện có.")
    @PreAuthorize("hasAnyAuthority('CRUISE_CREATE', 'CRUISE_READ', 'TRIP_READ')")
    @PostMapping("/preview-schedule")
    public ApiResponse<CruiseSchedulePreviewResponse> previewSchedule(
            @RequestBody @Valid CruiseSchedulePreviewRequest request) {
        return ApiResponse.success(scheduleMaterializationService.previewSchedule(
                request.getSourceVersionId(), request.getDepartureDatetime()));
    }

    @Operation(summary = "Xem lịch trình thực tế của chuyến khởi hành", description = "Đọc lại lịch trình (Itinerary, Days, CruiseStops) đã được tạo cho chuyến khởi hành (C04)")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'TRIP_READ', 'ITINERARY_READ')")
    @GetMapping("/{cruiseId}/schedule")
    public ApiResponse<CruiseSchedulePreviewResponse> getCruiseSchedule(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(scheduleMaterializationService.getCruiseSchedule(cruiseId));
    }

    @Operation(summary = "Tạo chuyến khởi hành mới", description = "Tạo Cruise DRAFT và snapshot atomic từ TripScheduleVersion đã PUBLISHED thuộc Trip chưa ARCHIVED. Bắt buộc sourceVersionId và expectedSourceVersionRevision khớp preview. Thời lượng kế thừa từ nguồn. Có thể gán tàu ngay hoặc gán sau. Yêu cầu Idempotency-Key header. Đọc snapshot, sourceContentHash, durationDays/durationNights và bằng chứng khung giờ cảng qua GET /cruise/{cruiseId}/schedule.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "STALE_PREVIEW: thiếu hoặc sai revision nguồn. LEGACY_SOURCE_MATERIALIZATION_BLOCKED: nguồn chứa SERVING_WINDOW lịch sử.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "SOURCE_VERSION_REQUIRED / VERSION_NOT_PUBLISHED / TRIP_ARCHIVED / SOURCE_DURATION_INVALID / PORT_CALL_WINDOW_VALIDATION_REQUIRED / ITINERARY_ALREADY_MATERIALIZED. Không ghi thành công một phần.")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_CREATE')")
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
        CruiseResponse response = idempotencyService.executeCruiseCreate(
                idempotencyKey, actorId, request, () -> service.create(request));
        return ApiResponse.success("Created Cruise successfully", response);
    }

    @Operation(summary = "Danh sách chuyến khởi hành", description = "Lấy danh sách các chuyến khởi hành (hỗ trợ phân trang và lọc theo trạng thái, tàu, loại chuyến, sourceTripId, sourceVersionId)")
    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<Page<CruiseResponse>> list(
            @Parameter(description = "Lọc theo trạng thái chuyến khởi hành") @RequestParam(required = false) CruiseStatus status,
            @Parameter(description = "Lọc theo ID tàu") @RequestParam(required = false) UUID shipId,
            @Parameter(description = "Lọc các chuyến khởi hành chưa gán tàu (unassigned=true)") @RequestParam(required = false) Boolean unassigned,
            @Parameter(description = "Lọc theo loại chuyến khởi hành (ONE_DAY, MULTI_DAYS)") @RequestParam(required = false) CruiseCruiseType cruiseType,
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
        if (bookingView) {
            page = page.map(this::sanitizeForPublic);
        }
        return ApiResponse.success(page);
    }

    @Operation(summary = "Chi tiết chuyến khởi hành", description = "Lấy thông tin chi tiết của một chuyến khởi hành theo ID")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}")
    public ApiResponse<CruiseResponse> detail(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @RequestParam(required = false) String saleState,
            org.springframework.security.core.Authentication auth) {
        validateSaleState(saleState);
        CruiseResponse response = service.detail(cruiseId);
        boolean hasCruiseRead = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        if (!hasCruiseRead || bookingOnly(auth) || saleState != null)
            service.requirePublicSaleVisible(cruiseId);
        if (!hasCruiseRead || bookingOnly(auth) || saleState != null) {
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
        res.setStopReason(null);
        res.setShipAssignmentSafety(null);
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

    @Operation(summary = "Cập nhật bản nháp chuyến khởi hành", description = "Cập nhật thông tin chuyến khởi hành (chỉ áp dụng khi chuyến ở trạng thái DRAFT)")
    @PreAuthorize("hasAuthority('CRUISE_UPDATE_DRAFT')")
    @PatchMapping("/{cruiseId}/draft")
    public ApiResponse<CruiseResponse> updateDraft(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseUpdateDraftRequest request) {
        return ApiResponse.success(service.updateDraft(cruiseId, request));
    }

    @Operation(summary = "Xóa bản nháp chuyến khởi hành", description = "Xóa chuyến khởi hành ở trạng thái DRAFT và chưa có đặt chỗ nào")
    @PreAuthorize("hasAuthority('CRUISE_DELETE_DRAFT')")
    @DeleteMapping("/{cruiseId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        service.deleteDraft(cruiseId);
        return ApiResponse.success("Deleted draft cruise successfully", null);
    }

    @Operation(summary = "Gán tàu cho chuyến khởi hành", description = "Quản lý lịch trình có CRUISE_ASSIGN_SHIP chọn tàu READY/IN_SERVICE cho chuyến DRAFT/PUBLISHED và kế hoạch cho phép sửa. Kiểm tra lịch, cấu hình, sức chứa hành khách do Ship quản lý và dữ liệu phụ thuộc; không có hạn mức sức chứa riêng của Departure.")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_ASSIGN_SHIP')")
    @PostMapping("/{cruiseId}/assign-ship")
    public ApiResponse<CruiseResponse> assignShip(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu cần gán", required = true) @RequestParam UUID shipId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.assignShip(cruiseId, shipId, actorId));
    }

    @Operation(summary = "Thay đổi tàu đã gán", description = "Quản lý lịch trình có CRUISE_ASSIGN_SHIP đổi tàu sau khi kiểm tra điều kiện hiện hành. Khi có dữ liệu phụ thuộc hoặc chuyến PUBLISHED, phải qua preview-change-impact/apply-change; dữ liệu chưa có cơ chế chuyển an toàn sẽ chặn.")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_ASSIGN_SHIP')")
    @PutMapping("/{cruiseId}/change-assigned-ship")
    public ApiResponse<CruiseResponse> changeAssignedShip(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu mới", required = true) @RequestParam UUID newShipId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.changeAssignedShip(cruiseId, newShipId, actorId));
    }

    @Operation(summary = "Hủy gán tàu khỏi chuyến khởi hành", description = "Gỡ bỏ tàu khỏi chuyến khởi hành (chỉ áp dụng khi chuyến ở trạng thái DRAFT và chưa có đặt chỗ)")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_ASSIGN_SHIP')")
    @PostMapping("/{cruiseId}/unassign-ship")
    public ApiResponse<CruiseResponse> unassignShip(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.unassignShip(cruiseId, actorId));
    }

    @Operation(summary = "Kiểm tra tính khả dụng của tàu đối với chuyến khởi hành", description = "Kiểm tra tàu có ACTIVE và không bị trùng lịch với chuyến khởi hành này không")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-ship-availability")
    public ApiResponse<Boolean> validateShipAvailability(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID tàu cần kiểm tra", required = true) @RequestParam UUID shipId) {
        return ApiResponse.success(service.validateShipAvailability(cruiseId, shipId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ của lịch trình chuyến khởi hành", description = "Xác nhận thời điểm trở về sau thời điểm khởi hành")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-schedule")
    public ApiResponse<Boolean> validateSchedule(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateSchedule(cruiseId));
    }

    @Operation(summary = "Kiểm tra sức chứa của chuyến khởi hành", description = "So sánh sức chứa tối đa của tàu với số lượng hành khách đã đặt chỗ hiện tại")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/validate-capacity")
    public ApiResponse<CruiseCapacityValidationResponse> validateCapacity(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateCapacity(cruiseId));
    }

    @Operation(summary = "Kiểm tra tính sẵn sàng vận hành (Operational Readiness)", description = "Đánh giá toàn diện: tàu đã gán và ACTIVE, có tầng và phòng, lịch trình hợp lệ, cảng hoạt động, hải trình đã cấu hình")
    @PreAuthorize("hasAuthority('CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/readiness")
    public ApiResponse<CruiseReadinessResponse> validateOperationalReadiness(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateOperationalReadiness(cruiseId));
    }

    @Deprecated
    @Operation(summary = "Phát hành chuyến khởi hành (Publish - Legacy / Operational materialization)",
               description = "Chuyển trạng thái từ DRAFT sang PUBLISHED để ghi nhận chuyến khởi hành đã sẵn sàng vận hành. LƯU Ý: Thao tác này KHÔNG mở bán thương mại cho hành khách (khách hàng chỉ có thể mua vé sau khi OPS mở bán qua POST /{cruiseId}/open-sale).")
    @PreAuthorize("hasAuthority('CRUISE_PUBLISH')")
    @PostMapping("/{cruiseId}/publish")
    public ApiResponse<CruiseResponse> publish(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.publish(cruiseId));
    }

    @Operation(summary = "Bắt đầu chuyến khởi hành (Start - BE-21)", description = "Chuyển trạng thái từ PUBLISHED sang IN_PROGRESS khi chuyến khởi hành chính thức bắt đầu (bắt buộc kiểm tra Departure Readiness)")
    @PreAuthorize("hasAuthority('CRUISE_START')")
    @PostMapping("/{cruiseId}/start")
    public ApiResponse<CruiseResponse> start(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.start(cruiseId, actorId));
    }

    @Operation(summary = "Hoàn tất chuyến khởi hành (Complete - BE-21)", description = "Chuyển trạng thái từ IN_PROGRESS sang COMPLETED khi tàu cập cảng an toàn và kết thúc chuyến khởi hành (kiểm tra checklist đóng chuyến)")
    @PreAuthorize("hasAuthority('CRUISE_COMPLETE')")
    @PostMapping("/{cruiseId}/complete")
    public ApiResponse<CruiseResponse> complete(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Ghi chú đóng chuyến và quyết toán", required = false) @RequestParam(required = false) String closureNotes,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.complete(cruiseId, actorId, closureNotes));
    }

    @Operation(summary = "Hủy chuyến khởi hành (Cancel)", description = "Hủy chuyến khởi hành (yêu cầu cung cấp lý do hủy, không thể hủy chuyến đã hoàn tất)")
    @PreAuthorize("hasAuthority('CRUISE_CANCEL')")
    @PostMapping("/{cruiseId}/cancel")
    public ApiResponse<CruiseResponse> cancel(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseCancelRequest request) {
        return ApiResponse.success(service.cancel(cruiseId, request.getReason()));
    }

    // ==================== CRUISE STAFF ASSIGNMENT ====================

    @Operation(summary = "Tài khoản và vai trò phân công nhân sự được hỗ trợ", description = "Danh sách theo quy tắc CruiseStaff hiện có; lịch trùng được kiểm tra lại khi phân công. Không phải API availability/coverage.")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STAFF_ASSIGN')")
    @GetMapping("/{cruiseId}/staff/options")
    public ApiResponse<CruiseStaffService.AssignmentOptions> staffOptions(@PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(staffService.assignmentOptions(cruiseId, principal.getUserId()));
    }

    @Operation(summary = "Phân công nhân viên / thủy thủ đoàn vào chuyến khởi hành", description = "Gán nhân viên vào chuyến khởi hành, kiểm tra nhân viên active và không bị trùng lịch tàu khác")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STAFF_ASSIGN')")
    @PostMapping("/{cruiseId}/staff")
    public ApiResponse<CruiseStaffResponse> assignStaff(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseStaffRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success("Assigned staff to cruise successfully",
                staffService.assignStaff(cruiseId, request, principal.getUserId()));
    }

    @Operation(summary = "Danh sách nhân viên / thủy thủ đoàn trên chuyến khởi hành", description = "Lấy danh sách nhân sự được phân công phục vụ chuyến khởi hành")
    @PreAuthorize("hasAuthority('CRUISE_STAFF_READ')")
    @GetMapping("/{cruiseId}/staff")
    public ApiResponse<List<CruiseStaffResponse>> listStaff(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(staffService.listStaff(cruiseId));
    }

    @Operation(summary = "Hủy phân công nhân viên khỏi chuyến khởi hành", description = "Gỡ bỏ nhân sự khỏi chuyến khởi hành")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STAFF_REMOVE')")
    @DeleteMapping("/{cruiseId}/staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeStaff(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID người dùng", required = true) @PathVariable UUID userId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        staffService.removeStaff(cruiseId, userId, principal.getUserId());
        return ApiResponse.success("Staff member relieved from cruise", null);
    }

    // ==================== CRUISE TOUR PACKAGES ====================

    @Operation(summary = "Gán gói dịch vụ du lịch (Tour Package) vào chuyến khởi hành", description = "Mở bán gói dịch vụ cho du khách tham gia chuyến khởi hành này")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    @PostMapping("/{cruiseId}/packages")
    public ApiResponse<CruiseTourPackageResponse> assignPackage(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseTourPackageRequest request) {
        return ApiResponse.success("Assigned package to cruise successfully", packageService.assignPackage(cruiseId, request));
    }

    @Operation(summary = "Danh sách gói dịch vụ mở bán trên chuyến khởi hành", description = "Lấy danh sách các tour package đang được kích hoạt cho chuyến khởi hành")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}/packages")
    public ApiResponse<List<CruiseTourPackageResponse>> listPackages(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            org.springframework.security.core.Authentication auth) {
        boolean internalRead = auth != null && !bookingOnly(auth)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        if (internalRead) return ApiResponse.success(packageService.listPackages(cruiseId));
        service.requirePubliclyVisible(cruiseId);
        var allowed = service.getPublicSellability(cruiseId).getAvailablePackages().stream()
                .filter(com.casms.ship.dto.response.PackageSellabilityItem::isSellable)
                .map(com.casms.ship.dto.response.PackageSellabilityItem::getTourPackageId)
                .collect(java.util.stream.Collectors.toSet());
        return ApiResponse.success(packageService.listPackages(cruiseId).stream()
                .filter(p -> allowed.contains(p.getTourPackageId())).toList());
    }

    @Operation(summary = "Gỡ gói dịch vụ khỏi chuyến khởi hành", description = "Ngừng mở bán gói dịch vụ trên chuyến khởi hành")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    @DeleteMapping("/{cruiseId}/packages/{packageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removePackage(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "ID gói dịch vụ", required = true) @PathVariable UUID packageId) {
        packageService.removePackage(cruiseId, packageId);
        return ApiResponse.success("Tour package removed from cruise", null);
    }

    // ==================== CRUISE MANIFEST ====================

    @Operation(summary = "Xuất báo cáo danh sách hành khách và thuyền viên (Cruise Manifest)", description = "Cung cấp danh sách hành khách đã đặt phòng, thủy thủ đoàn và thông số tải trọng cho cơ quan cảng vụ")
    @PreAuthorize("hasAuthority('CRUISE_MANIFEST_READ')")
    @GetMapping("/{cruiseId}/manifest")
    public ApiResponse<CruiseManifestResponse> getManifest(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId) {
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

    @Operation(summary = "Kiểm tra tính tương thích và khả dụng của tàu (C05)", description = "Đánh giá chi tiết trạng thái, cấu hình boong/phòng, lịch trống và sức chứa của tàu đối với chuyến khởi hành")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_ASSIGN_SHIP', 'CRUISE_CHANGE_SHIP', 'SHIP_READ')")
    @GetMapping("/{cruiseId}/ship-compatibility")
    public ApiResponse<ShipCompatibilityResponse> checkShipCompatibility(
            @PathVariable UUID cruiseId,
            @RequestParam UUID shipId) {
        return ApiResponse.success(service.checkShipCompatibility(cruiseId, shipId));
    }

    @Operation(summary = "Xem trước tác động khi đổi tàu (C05, C11)", description = "Đánh giá ảnh hưởng đến sức chứa, phân bổ phòng và các hoạt động trên tàu khi đổi sang tàu khác")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_CHANGE_SHIP', 'CRUISE_ASSIGN_SHIP', 'SHIP_READ')")
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
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE', 'CRUISE_UPDATE_DRAFT', 'CRUISE_ASSIGN_SHIP')")
    @PostMapping(value = {"/{cruiseId}/preview-change-impact", "/{cruiseId}/change-impact"})
    public ApiResponse<CruiseChangeImpactResponse> previewChangeImpact(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseChangeImpactRequest request) {
        return ApiResponse.success(cruiseImpactAssessmentService.previewChangeImpact(cruiseId, request));
    }

    @Operation(summary = "Khởi tạo công việc xử lý tác động thay đổi (C17)", description = "Lưu lại các công việc xử lý tác động thay đổi chuyến khởi hành vào cơ sở dữ liệu")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE', 'OPERATIONAL_ISSUE_CREATE')")
    @PostMapping("/{cruiseId}/impact-tasks/generate")
    public ApiResponse<List<CruiseImpactTaskResponse>> generateImpactTasks(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseChangeImpactRequest request) {
        return ApiResponse.success(cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruiseId, request));
    }

    @Operation(summary = "Danh sách công việc xử lý tác động (C17)", description = "Tra cứu các công việc xử lý tác động phát sinh khi chuyến khởi hành bị thay đổi")
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
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_STATUS_CHANGE') or (hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_ASSIGN_SHIP') and #request.newShipId != null and #request.newDepartureDatetime == null and #request.newReturnDatetime == null and #request.newDeparturePortId == null and #request.newReturnPortId == null)")
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

    @Operation(summary = "Lấy báo cáo tồn chỗ và năng lực phục vụ của chuyến khởi hành (BE-07 / C08)", description = "Tra cứu chi tiết tồn phòng theo từng hạng và tổng sức chứa khả dụng phục vụ mở bán")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'BOOKING_READ')")
    @GetMapping("/{cruiseId}/inventory-summary")
    public ApiResponse<CruiseInventorySummaryResponse> getInventorySummary(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.getInventorySummary(cruiseId));
    }

    @Operation(summary = "Đánh giá điều kiện mở bán của chuyến khởi hành (BE-08A / C09)", description = "Kiểm tra toàn diện điều kiện mở bán bao gồm vận hành, lịch trình, sản phẩm/gói dịch vụ, tồn chỗ và giá")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/sale-readiness")
    public ApiResponse<CruiseSaleReadinessResponse> getSaleReadiness(
            @PathVariable UUID cruiseId, @RequestParam(defaultValue = "WHOLE_CRUISE") String saleScope,
            @RequestParam(required = false) java.util.Set<UUID> selectedPackageIds,
            @RequestParam(required = false) java.util.Set<UUID> selectedCruisePackageOfferIds) {
        return ApiResponse.success(service.validateSaleReadiness(cruiseId, saleScope, selectedPackageIds,selectedCruisePackageOfferIds));
    }

    @Operation(summary = "Đánh giá điều kiện khởi hành của chuyến khởi hành (Departure Readiness Gate - BE-08)", description = "Kiểm tra toàn diện điều kiện khởi hành bao gồm thuyền trưởng, nhân sự, và 5 phân hệ chuẩn bị liên phòng ban")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_VALIDATE')")
    @GetMapping("/{cruiseId}/departure-readiness")
    public ApiResponse<CruiseReadinessResponse> getDepartureReadiness(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.validateDepartureReadiness(cruiseId));
    }

    @Operation(summary = "Mở bán chuyến khởi hành hoặc sản phẩm dịch vụ (BE-08B / BE-13 / BE-H04)", description = "Kích hoạt trạng thái PUBLISHED cho chuyến khởi hành và OPEN_FOR_SALE cho lịch trình sau khi đạt kiểm tra sale readiness theo scope")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_OPEN_SALE')")
    @PostMapping("/{cruiseId}/open-sale")
    public ApiResponse<CruiseResponse> openSale(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid @jakarta.validation.constraints.NotNull CruiseOpenSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.openSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Kích hoạt gói dịch vụ để mở bán (BE-12)", description = "Chuyển trạng thái gói dịch vụ từ cấu hình danh mục sang sẵn sàng bán")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_OPEN_SALE')")
    @PostMapping("/{cruiseId}/packages/{tourPackageId}/activate")
    public ApiResponse<CruiseTourPackageResponse> activatePackage(
            @PathVariable UUID cruiseId,
            @PathVariable UUID tourPackageId) {
        return ApiResponse.success(packageService.activatePackage(cruiseId, tourPackageId));
    }

    @Operation(summary = "Hủy kích hoạt bán gói dịch vụ (BE-12)", description = "Đưa gói dịch vụ về lại trạng thái cấu hình mà không xóa khỏi chuyến khởi hành")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STOP_SALE')")
    @PostMapping("/{cruiseId}/packages/{tourPackageId}/deactivate")
    public ApiResponse<CruiseTourPackageResponse> deactivatePackage(
            @PathVariable UUID cruiseId,
            @PathVariable UUID tourPackageId) {
        return ApiResponse.success(packageService.deactivatePackage(cruiseId, tourPackageId));
    }

    @Operation(summary = "Tạm dừng mở bán chuyến khởi hành hoặc gói dịch vụ cụ thể (BE-08B / C16)", description = "Tạm dừng nhận booking mới khi điều kiện vận hành bị vi phạm mà không làm hủy các booking hiện có")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STOP_SALE')")
    @PostMapping("/{cruiseId}/stop-sale")
    public ApiResponse<CruiseResponse> stopSale(
            @PathVariable UUID cruiseId,
            @RequestBody @Valid CruiseStopSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.stopSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Tiếp tục mở bán sau khi tạm dừng (BE-08B / C16)", description = "Mở lại bán cho toàn bộ chuyến khởi hành hoặc gói dịch vụ sau khi kiểm tra lại sale readiness")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_RESUME_SALE')")
    @PostMapping("/{cruiseId}/resume-sale")
    public ApiResponse<CruiseResponse> resumeSale(
            @PathVariable UUID cruiseId,
            @RequestBody(required = false) CruiseResumeSaleRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.resumeSale(cruiseId, request, actorId));
    }

    @Operation(summary = "Truy vấn tình trạng mở bán công khai authoritative (BE-15 Public Sellability)",
               description = "Cung cấp nguồn sự thật duy nhất (Single Source of Truth) về trạng thái mở bán của chuyến khởi hành, từng hạng phòng và gói tour kèm theo mà không để lộ dữ liệu nhân sự, manifest hay nhà cung cấp.")
    @PreAuthorize("permitAll()")
    @GetMapping("/{cruiseId}/sellability")
    public ApiResponse<CruiseSellabilityResponse> getPublicSellability(
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            org.springframework.security.core.Authentication auth) {
        boolean internalRead = auth != null && !bookingOnly(auth)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CRUISE_READ"));
        if (!internalRead) service.requirePubliclyVisible(cruiseId);
        return ApiResponse.success(service.getPublicSellability(cruiseId));
    }

    @Operation(summary = "Bàn giao dữ liệu và thông tin manifest cho hệ thống điều hành (BE-09 / C14)", description = "Trích xuất context chuyến khởi hành, danh sách manifest phân biệt rõ đặt chỗ, check-in và lên tàu cho bộ phận cảng")
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
            @Parameter(description = "ID chuyến khởi hành", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Lọc theo loại sự kiện", required = false) @RequestParam(required = false) String eventType) {
        return ApiResponse.success(cruiseAuditService.listAuditEvents(cruiseId, eventType));
    }
}
