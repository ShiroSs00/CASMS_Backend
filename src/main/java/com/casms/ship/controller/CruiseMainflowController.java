package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseMainflowService;
import com.casms.ship.service.CruisePlanPackageSelectionService;
import com.casms.ship.service.DepartureSaleTermsService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruise-plans")
public class CruiseMainflowController {
    private final CruiseMainflowService service;

    private final CruisePlanPackageSelectionService selections;
    private final DepartureSaleTermsService saleTerms;
    private final com.casms.notification.service.NotificationService notifications;

    @GetMapping("/sale-terms-requests")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<List<DepartureSaleTermsService.SetupRequest>> saleTermsRequests() {
        return ApiResponse.success(saleTerms.setupRequests());
    }

    @PostMapping("/{cruiseId}/sale-terms-request")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<Boolean> requestSaleTerms(@PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(saleTerms.requestSetup(cruiseId, principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/sale-terms-request")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_READ')")
    public ApiResponse<Boolean> pendingSaleTermsRequest(@PathVariable UUID cruiseId) {
        return ApiResponse.success(saleTerms.pendingSetup(cruiseId));
    }

    @GetMapping("/{cruiseId}/cruise-packages")
    @PreAuthorize("hasAuthority('CRUISE_READ') and hasAnyRole('CRUISE_MANAGER','OPERATIONS_MANAGER')")
    public ApiResponse<CruisePlanPackageSelectionService.View> packageSelection(@PathVariable UUID cruiseId) {
        return ApiResponse.success(selections.read(cruiseId));
    }

    @PutMapping("/{cruiseId}/cruise-packages")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruisePlanPackageSelectionService.View> selectPackages(
            @PathVariable UUID cruiseId, @Valid @RequestBody CruisePlanPackageSelectionService.Write input,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(selections.save(cruiseId, input, principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/commercial-terms/sale")
    @PreAuthorize("(hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')) or "
            + "(hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_READ'))")
    public ApiResponse<DepartureSaleTermsService.View> saleTerms(@PathVariable UUID cruiseId) {
        return ApiResponse.success(saleTerms.read(cruiseId));
    }

    @GetMapping("/{cruiseId}/commercial-terms/sale/history")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<List<DepartureSaleTermsService.HistoryEntry>> saleTermsHistory(@PathVariable UUID cruiseId) {
        return ApiResponse.success(saleTerms.history(cruiseId));
    }

    @PutMapping("/{cruiseId}/commercial-terms/sale")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<DepartureSaleTermsService.View> saveSaleTerms(
            @PathVariable UUID cruiseId, @Valid @RequestBody DepartureSaleTermsService.Write input,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(saleTerms.save(cruiseId, input, principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/ship-assignment-context")
    @PreAuthorize(
            "hasRole('CRUISE_MANAGER') and"
                + " hasAnyAuthority('CRUISE_ASSIGN_SHIP','CRUISE_CHANGE_SHIP','CRUISE_UNASSIGN_SHIP')")
    public ApiResponse<CruiseMainflowService.AssignmentContext> assignmentContext(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.assignmentContext(cruiseId));
    }

    @GetMapping("/{cruiseId}")
    @PreAuthorize(
            "hasAuthority('CRUISE_READ') and"
                + " hasAnyRole('CRUISE_MANAGER','OPERATIONS_MANAGER','SHIP_MANAGER')")
    public ApiResponse<CruiseMainflowService.View> get(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.get(cruiseId));
    }

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_READ')")
    public ApiResponse<List<CruiseMainflowService.View>> queue(
            @RequestParam(defaultValue = "false") boolean includeReviewed) {
        return ApiResponse.success(includeReviewed ? service.operationsWorkspace() : service.queue());
    }

    @PutMapping("/{cruiseId}/windows")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruiseMainflowService.View> windows(
            @PathVariable UUID cruiseId,
            @RequestBody CruiseMainflowService.Windows request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.windows(cruiseId, request, principal.getUserId()));
    }

    @PostMapping("/{cruiseId}/{action:submit|withdraw}")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruiseMainflowService.View> submit(
            @PathVariable UUID cruiseId,
            @PathVariable String action,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CruiseMainflowService.View view = service.transition(
                cruiseId,
                action.toUpperCase(java.util.Locale.ROOT),
                request,
                principal.getUserId());
        if ("submit".equalsIgnoreCase(action)) {
            String cruiseLabel = formatCruiseLabel(view);
            notifications.notifyRole(
                    com.casms.identity.enums.Role.OPERATIONS_MANAGER,
                    "CRUISE_PLAN_SUBMITTED",
                    "Hồ sơ chuyến " + view.cruiseCode() + " chờ xét duyệt",
                    "Quản lý lịch trình vừa nộp hồ sơ chuyến "
                            + cruiseLabel
                            + " để Quản lý vận hành xét duyệt.");
        }
        return ApiResponse.success(view);
    }

    @PostMapping("/{cruiseId}/{action:approve|reject|request-revision}")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('ITINERARY_APPROVE')")
    public ApiResponse<CruiseMainflowService.View> review(
            @PathVariable UUID cruiseId,
            @PathVariable String action,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CruiseMainflowService.View view = service.transition(
                cruiseId,
                action.toUpperCase(java.util.Locale.ROOT).replace('-', '_'),
                request,
                principal.getUserId());
        String cruiseLabel = formatCruiseLabel(view);
        if ("approve".equalsIgnoreCase(action)) {
            notifications.notifyRole(
                    com.casms.identity.enums.Role.CRUISE_MANAGER,
                    "CRUISE_PLAN_APPROVED",
                    "Hồ sơ chuyến " + view.cruiseCode() + " đã được phê duyệt",
                    "Quản lý vận hành đã phê duyệt hồ sơ chuyến " + cruiseLabel + ".");
            notifications.notifyRole(
                    com.casms.identity.enums.Role.SHIP_MANAGER,
                    "CRUISE_PLAN_APPROVED",
                    "Chuyến " + view.cruiseCode() + " đã được phê duyệt",
                    "Hồ sơ chuyến "
                            + cruiseLabel
                            + " đã được phê duyệt và sẵn sàng phối hợp chuẩn bị tàu.");
            notifications.notifyRole(
                    com.casms.identity.enums.Role.ACTIVITY_SERVICE_MANAGER,
                    "CRUISE_PLAN_APPROVED",
                    "Chuyến " + view.cruiseCode() + " đã được phê duyệt",
                    "Hồ sơ chuyến "
                            + cruiseLabel
                            + " đã được phê duyệt để phối hợp chương trình và dịch vụ.");
        } else {
            boolean rejected = "reject".equalsIgnoreCase(action);
            String reasonText =
                    request.reason() != null && !request.reason().isBlank()
                            ? " Lý do: " + request.reason().trim()
                            : "";
            notifications.notifyRole(
                    com.casms.identity.enums.Role.CRUISE_MANAGER,
                    rejected ? "CRUISE_PLAN_REJECTED" : "CRUISE_PLAN_REVISION_REQUESTED",
                    (rejected ? "Hồ sơ chuyến " : "Hồ sơ chuyến ")
                            + view.cruiseCode()
                            + (rejected ? " bị từ chối" : " cần chỉnh sửa"),
                    "Quản lý vận hành đã "
                            + (rejected ? "từ chối" : "yêu cầu chỉnh sửa")
                            + " hồ sơ chuyến "
                            + cruiseLabel
                            + "."
                            + reasonText);
        }
        return ApiResponse.success(view);
    }

    @PostMapping("/{cruiseId}/ready")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_VALIDATE')")
    public ApiResponse<CruiseMainflowService.View> ready(
            @PathVariable UUID cruiseId,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CruiseMainflowService.View view = service.setReady(cruiseId, request, principal.getUserId());
        String cruiseLabel = formatCruiseLabel(view);
        notifications.notifyRole(
                com.casms.identity.enums.Role.CRUISE_MANAGER,
                "CRUISE_READY_FOR_SALE",
                "Chuyến " + view.cruiseCode() + " sẵn sàng mở bán",
                "Quản lý vận hành đã xác nhận chuyến "
                        + cruiseLabel
                        + " đạt điều kiện sẵn sàng mở bán.");
        return ApiResponse.success(view);
    }

    private String formatCruiseLabel(CruiseMainflowService.View view) {
        if (view.cruiseName() != null && !view.cruiseName().isBlank()) {
            return view.cruiseCode() + " — " + view.cruiseName().trim();
        }
        return view.cruiseCode();
    }
}
