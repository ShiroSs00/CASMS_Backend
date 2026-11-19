package com.casms.staffing;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruises")
public class StaffingWorkflowController {
    private final StaffingWorkflowService workflow;

    @GetMapping("/{cruiseId}/staffing-requests")
    public ApiResponse<StaffingWorkflowService.Preparation> preparation(
            @PathVariable UUID cruiseId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.preparation(cruiseId, actor));
    }

    @PostMapping("/{cruiseId}/staffing-requests")
    public ApiResponse<StaffingWorkflowService.Snapshot> submit(
            @PathVariable UUID cruiseId, @RequestBody StaffingWorkflowService.CreateRequest input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.submit(cruiseId, input, actor));
    }

    @GetMapping("/staffing-requests/inbox")
    public ApiResponse<List<StaffingWorkflowService.Snapshot>> inbox(
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.inbox(actor));
    }

    @GetMapping("/staffing-requests/ship-manager-needed")
    public ApiResponse<List<StaffingWorkflowService.ShipManagerNeeded>> cruisesWithoutShipManager(
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.cruisesWithoutShipManager(actor));
    }

    @GetMapping("/staffing-requests/{requestId}")
    public ApiResponse<StaffingWorkflowService.Snapshot> detail(
            @PathVariable UUID requestId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.detail(requestId, actor));
    }

    @GetMapping("/staffing-requests/{requestId}/review-details")
    public ApiResponse<StaffingWorkflowService.ReviewDetails> reviewDetails(
            @PathVariable UUID requestId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.reviewDetails(requestId, actor));
    }

    @PutMapping("/staffing-requests/{requestId}/service-placements/{serviceId}")
    public ApiResponse<StaffingWorkflowService.ReviewDetails> placeService(
            @PathVariable UUID requestId, @PathVariable UUID serviceId,
            @Valid @RequestBody StaffingWorkflowService.ServicePlacementInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.placeService(requestId, serviceId, input, actor));
    }

    @PutMapping("/staffing-requests/{requestId}/area-pos/{areaId}")
    public ApiResponse<StaffingWorkflowService.ReviewDetails> placeAreaPos(
            @PathVariable UUID requestId, @PathVariable UUID areaId,
            @Valid @RequestBody StaffingWorkflowService.AreaPosInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.placeAreaPos(requestId, areaId, input, actor));
    }

    @PutMapping("/staffing-requests/{requestId}/ship-plan")
    public ApiResponse<StaffingWorkflowService.ReviewDetails> saveShipPlan(
            @PathVariable UUID requestId, @Valid @RequestBody StaffingWorkflowService.ShipPlanInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.saveShipPlan(requestId, input, actor));
    }

    @PutMapping("/staffing-requests/{requestId}/needs/{needId}/assignments/{userId}")
    public ApiResponse<StaffingWorkflowService.Snapshot> assignNeed(
            @PathVariable UUID requestId, @PathVariable UUID needId, @PathVariable UUID userId,
            @Valid @RequestBody StaffingWorkflowService.NeedAssignmentInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.assignNeed(requestId, needId, userId, input, actor));
    }

    @PostMapping("/staffing-requests/{requestId}/{action}")
    public ApiResponse<StaffingWorkflowService.Snapshot> review(
            @PathVariable UUID requestId, @PathVariable String action,
            @RequestBody StaffingWorkflowService.ReviewRequest input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.transition(requestId, action.toUpperCase(Locale.ROOT), input, actor));
    }
}
