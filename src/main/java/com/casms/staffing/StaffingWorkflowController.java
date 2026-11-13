package com.casms.staffing;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/staffing-requests/{requestId}")
    public ApiResponse<StaffingWorkflowService.Snapshot> detail(
            @PathVariable UUID requestId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.detail(requestId, actor));
    }

    @PostMapping("/staffing-requests/{requestId}/{action}")
    public ApiResponse<StaffingWorkflowService.Snapshot> review(
            @PathVariable UUID requestId, @PathVariable String action,
            @RequestBody StaffingWorkflowService.ReviewRequest input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(workflow.transition(requestId, action.toUpperCase(Locale.ROOT), input, actor));
    }
}
