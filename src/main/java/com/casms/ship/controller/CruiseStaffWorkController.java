package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseStaffWorkService;
import com.casms.ship.service.CruiseStaffWorkService.ActivityKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CRUISE_STAFF_ASSIGN')")
public class CruiseStaffWorkController {
    private final CruiseStaffWorkService service;

    public record ServiceInput(@NotNull UUID serviceId) {}

    @GetMapping({"/cruise/{cruiseId}/staff/work-plan", "/cruises/{cruiseId}/staff/work-plan"})
    @PreAuthorize("hasAnyAuthority('CRUISE_STAFF_ASSIGN','CRUISE_STAFF_READ')")
    public ApiResponse<CruiseStaffWorkService.WorkPlan> workPlan(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.workPlan(cruiseId));
    }

    @GetMapping("/staff/{userId}/activity-qualifications")
    public ApiResponse<List<CruiseStaffWorkService.Qualification>> qualifications(@PathVariable UUID userId) {
        return ApiResponse.success(service.qualifications(userId));
    }

    @PutMapping("/staff/{userId}/activity-qualifications/{kind}/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addQualification(
            @PathVariable UUID userId, @PathVariable ActivityKind kind,
            @PathVariable UUID definitionId, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.addQualification(userId, kind, definitionId, actor.getUserId());
    }

    @DeleteMapping("/staff/{userId}/activity-qualifications/{kind}/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeQualification(
            @PathVariable UUID userId, @PathVariable ActivityKind kind,
            @PathVariable UUID definitionId, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.removeQualification(userId, kind, definitionId, actor.getUserId());
    }

    @PutMapping({"/cruise/{cruiseId}/staff/{userId}/service", "/cruises/{cruiseId}/staff/{userId}/service"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignService(
            @PathVariable UUID cruiseId, @PathVariable UUID userId,
            @Valid @RequestBody ServiceInput input, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.assignService(cruiseId, userId, input.serviceId(), actor.getUserId());
    }

    @DeleteMapping({"/cruise/{cruiseId}/staff/{userId}/service", "/cruises/{cruiseId}/staff/{userId}/service"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeService(
            @PathVariable UUID cruiseId, @PathVariable UUID userId,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        service.removeService(cruiseId, userId, actor.getUserId());
    }

    @PutMapping({
        "/cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}",
        "/cruises/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignActivity(
            @PathVariable UUID cruiseId, @PathVariable UUID userId, @PathVariable ActivityKind kind,
            @PathVariable UUID occurrenceId, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.assignActivity(cruiseId, userId, kind, occurrenceId, actor.getUserId());
    }

    @DeleteMapping({
        "/cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}",
        "/cruises/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeActivity(
            @PathVariable UUID cruiseId, @PathVariable UUID userId, @PathVariable ActivityKind kind,
            @PathVariable UUID occurrenceId, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.removeActivity(cruiseId, userId, kind, occurrenceId, actor.getUserId());
    }
}
