package com.casms.pos;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@Tag(name="POS area access",description="Cruise-aware employee assignments and explicit POS-area-service capabilities")
@PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
public class PosAssignmentController {
    private final PosAccessService service;

    @Operation(summary="List employee assignments for a physical area, including inactive assignments")
    @GetMapping("/ships/{shipId}/areas/{areaId}/staff")
    public ApiResponse<List<PosDtos.AreaStaff>> staff(@PathVariable UUID shipId,@PathVariable UUID areaId) {
        return ApiResponse.success(service.staff(shipId,areaId));
    }
    @Operation(summary="Assign an eligible cruise staff member to an area with an optional access window",
            description="The employee needs POS_ACCESS and an ACTIVE CruiseStaff assignment on this ship. Changes revoke existing sessions for this assignment; no global role or operation permission is granted.")
    @PutMapping("/ships/{shipId}/areas/{areaId}/staff/{userId}")
    public ApiResponse<PosDtos.AreaStaff> assignStaff(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID userId,
            @Valid @RequestBody PosDtos.AreaStaffInput input,@AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.assignStaff(shipId,areaId,userId,input,actor));
    }
    @Operation(summary="Revoke an employee's area assignment for a selected cruise and revoke its sessions")
    @DeleteMapping("/ships/{shipId}/areas/{areaId}/staff/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignStaff(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID userId,@RequestParam UUID cruiseId) {
        service.unassignStaff(shipId,areaId,userId,cruiseId);
    }
    @Operation(summary="List the areas linked to a POS; the original area remains its default")
    @GetMapping("/pos-terminals/{terminalId}/areas")
    public ApiResponse<List<PosDtos.TerminalArea>> areas(@PathVariable UUID terminalId) {return ApiResponse.success(service.areas(terminalId));}

    @Operation(summary="Link an additional area on the same ship to this POS",
            description="New links inherit canonical resource operations through area assignments, capped by terminal permissions. Explicit POS policies can restrict them further. Changing the link revokes sessions using that area.")
    @PutMapping("/pos-terminals/{terminalId}/areas/{areaId}")
    public ApiResponse<PosDtos.TerminalArea> linkArea(@PathVariable UUID terminalId,@PathVariable UUID areaId,
            @Valid @RequestBody PosDtos.AreaLinkInput input) {return ApiResponse.success(service.linkArea(terminalId,areaId,input.enabled()));}

    @Operation(summary="Disable an area on this POS and revoke sessions in that area, retaining history")
    @DeleteMapping("/pos-terminals/{terminalId}/areas/{areaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkArea(@PathVariable UUID terminalId,@PathVariable UUID areaId) {service.linkArea(terminalId,areaId,false);}

    @Operation(summary="List the service permissions configured for this exact POS and area")
    @GetMapping("/pos-terminals/{terminalId}/areas/{areaId}/services")
    public ApiResponse<List<PosDtos.TerminalService>> policies(@PathVariable UUID terminalId,@PathVariable UUID areaId) {
        return ApiResponse.success(service.policies(terminalId,areaId));
    }
    @Operation(summary="Restrict or inherit operation permissions for a service in an area on this POS",
            description="Omit permissions to inherit; explicit permissions must be within source, terminal and area-service capabilities. Every POS call re-evaluates current permissions; an existing session cannot retain removed capabilities.")
    @PutMapping("/pos-terminals/{terminalId}/areas/{areaId}/services/{serviceId}")
    public ApiResponse<PosDtos.TerminalService> policy(@PathVariable UUID terminalId,@PathVariable UUID areaId,@PathVariable UUID serviceId,
            @Valid @RequestBody PosDtos.AssignmentInput input) {return ApiResponse.success(service.policy(terminalId,areaId,serviceId,input));}

    @Operation(summary="Deny this service on this POS and area, retaining an explicit disabled override")
    @DeleteMapping("/pos-terminals/{terminalId}/areas/{areaId}/services/{serviceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePolicy(@PathVariable UUID terminalId,@PathVariable UUID areaId,@PathVariable UUID serviceId) {service.removePolicy(terminalId,areaId,serviceId);}
}
