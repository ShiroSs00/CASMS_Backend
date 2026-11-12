package com.casms.notification.controller;

import com.casms.common.response.ApiResponse;
import com.casms.notification.service.StaffAssignmentEmailRenderer.Rendered;
import com.casms.notification.service.StaffAssignmentEmailRenderer.Template;
import com.casms.notification.service.StaffAssignmentEmailService;
import com.casms.security.principal.CasmsPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CRUISE_STAFF_ASSIGN')")
public class StaffAssignmentEmailController {
    private final StaffAssignmentEmailService service;

    public record DeliveryStatus(StaffAssignmentEmailService.Status status) {}

    @GetMapping("/staff/assignment-email-template")
    public ApiResponse<StaffAssignmentEmailService.PublishedTemplate> template() {
        return ApiResponse.success(service.currentTemplate());
    }

    @PostMapping("/staff/assignment-email-template/preview")
    public ApiResponse<Rendered> preview(@RequestBody @Valid Template template) {
        return ApiResponse.success(service.preview(template));
    }

    @PutMapping("/staff/assignment-email-template")
    public ApiResponse<StaffAssignmentEmailService.PublishedTemplate> publish(
            @RequestBody @Valid Template template, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.publish(template, actor.getUserId()));
    }

    @GetMapping({
        "/cruise/{cruiseId}/staff/assignments/{assignmentId}/email",
        "/cruises/{cruiseId}/staff/assignments/{assignmentId}/email"
    })
    public ApiResponse<DeliveryStatus> status(@PathVariable UUID cruiseId, @PathVariable UUID assignmentId) {
        return ApiResponse.success(new DeliveryStatus(service.status(cruiseId, assignmentId)));
    }

    @PostMapping({
        "/cruise/{cruiseId}/staff/assignments/{assignmentId}/email/retry",
        "/cruises/{cruiseId}/staff/assignments/{assignmentId}/email/retry"
    })
    public ApiResponse<DeliveryStatus> retry(
            @PathVariable UUID cruiseId, @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(new DeliveryStatus(service.retry(cruiseId, assignmentId, actor.getUserId())));
    }
}
