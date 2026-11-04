package com.casms.pos;

import com.casms.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ships/{shipId}/areas/{areaId}/staff/{userId}/resources/{resourceType}")
public class PosStaffResourceController {
    private final PosStaffResourceService service;

    @Operation(
            summary =
                    "List the employee's specific service or activity assignments for an area and"
                        + " cruise")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping
    public ApiResponse<List<PosDtos.StaffResource>> list(
            @PathVariable UUID shipId,
            @PathVariable UUID areaId,
            @PathVariable UUID userId,
            @PathVariable PosPermission.ResourceType resourceType,
            @RequestParam UUID cruiseId) {
        return ApiResponse.success(service.list(shipId, areaId, userId, cruiseId, resourceType));
    }

    @Operation(
            summary = "Assign a specific service or activity to an employee",
            description =
                    "Requires an existing area/cruise assignment and an area-linked resource."
                        + " Omitted permissions inherit the source live; explicit sets only"
                        + " restrict it. No resource assignment means deny. Operations Manager does"
                        + " not need employee assignments.")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PutMapping("/{resourceId}")
    public ApiResponse<PosDtos.StaffResource> assign(
            @PathVariable UUID shipId,
            @PathVariable UUID areaId,
            @PathVariable UUID userId,
            @PathVariable PosPermission.ResourceType resourceType,
            @PathVariable UUID resourceId,
            @RequestParam UUID cruiseId,
            @Valid @RequestBody PosDtos.AssignmentInput input) {
        return ApiResponse.success(
                service.assign(shipId, areaId, userId, cruiseId, resourceType, resourceId, input));
    }

    @Operation(
            summary =
                    "Remove an employee resource assignment; subsequent requests immediately lose"
                        + " its operations")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @DeleteMapping("/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable UUID shipId,
            @PathVariable UUID areaId,
            @PathVariable UUID userId,
            @PathVariable PosPermission.ResourceType resourceType,
            @PathVariable UUID resourceId,
            @RequestParam UUID cruiseId) {
        service.remove(shipId, areaId, userId, cruiseId, resourceType, resourceId);
    }
}
