package com.casms.booking.cancellation.controller;

import com.casms.booking.cancellation.entity.CancellationCase.Scenario;
import com.casms.booking.cancellation.service.CancellationWorkflowService;
import com.casms.booking.cancellation.service.CancellationWorkflowService.CaseView;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(name = "casms.booking.cancellation-workflow.enabled", havingValue = "true")
public class CancellationController {
    private final CancellationWorkflowService service;

    public record Intake(@NotNull Scenario scenario, @NotBlank @Size(max = 1000) String reason, UUID issueId) {
        @JsonAnySetter
        public void rejectUnknownField(String field, Object value) {
            throw new IllegalArgumentException("Unsupported cancellation field: " + field);
        }
    }

    public record Decision(@NotNull @Min(0) Long revision, @Size(max = 1000) String reason) {
        @JsonAnySetter
        public void rejectUnknownField(String field, Object value) {
            throw new IllegalArgumentException("Unsupported cancellation field: " + field);
        }
    }

    @PostMapping("/booking/{bookingId}/cancellations")
    public ApiResponse<CaseView> request(@PathVariable UUID bookingId, @Valid @RequestBody Intake body,
            @RequestHeader("Idempotency-Key") String key, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.request(bookingId, body.scenario(), body.reason(), body.issueId(), key, actor));
    }

    @GetMapping("/booking/{bookingId}/cancellations")
    public ApiResponse<List<CaseView>> history(@PathVariable UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.history(bookingId, actor));
    }

    @GetMapping("/cancellations")
    public ApiResponse<Page<CaseView>> queue(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID issueId,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.queue(page, size, issueId, actor));
    }

    @GetMapping("/cancellations/{id}")
    public ApiResponse<CaseView> detail(@PathVariable UUID id, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.detail(id, actor));
    }

    @PostMapping("/cancellations/{id}/{action:verify|approve|reject|withdraw}")
    public ApiResponse<CaseView> transition(@PathVariable UUID id, @PathVariable String action,
            @Valid @RequestBody Decision body, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.transition(id, action, body.revision(), body.reason(), actor));
    }
}
