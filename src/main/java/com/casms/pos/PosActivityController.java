package com.casms.pos;

import com.casms.activity.controller.ActivityController.*;
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
import java.util.*;

@RestController @RequiredArgsConstructor
@Tag(name="POS onboard activities",description="Live resource permissions with area, device, employee and cruise checks")
public class PosActivityController {
    private final PosActivityService service;
    private static final String MANAGE="hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked";
    private static final String ACCESS="hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked";

    @Operation(summary="List onboard activities linked to a physical area and their inherited permissions")
    @PreAuthorize(MANAGE) @GetMapping("/ships/{shipId}/areas/{areaId}/activities")
    public ApiResponse<List<PosDtos.AreaActivity>> assignments(@PathVariable UUID shipId,@PathVariable UUID areaId) {
        return ApiResponse.success(service.assignments(shipId,areaId));
    }
    @Operation(summary="Link an activity to an area on its ship",description="Omit permissions to inherit the activity policy. Explicit permissions can only restrict. Specified activity deck/cabin must match the area's deck.")
    @PreAuthorize(MANAGE) @PutMapping("/ships/{shipId}/areas/{areaId}/activities/{activityId}")
    public ApiResponse<PosDtos.AreaActivity> assign(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID activityId,@Valid @RequestBody PosDtos.AssignmentInput input) {
        return ApiResponse.success(service.assign(shipId,areaId,activityId,input));
    }
    @Operation(summary="Unlink an activity from an area, retaining registrations and attendance history")
    @PreAuthorize(MANAGE) @DeleteMapping("/ships/{shipId}/areas/{areaId}/activities/{activityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID activityId) {service.unassign(shipId,areaId,activityId);}

    @Operation(summary="Read inherited or restricted activity capabilities for this POS and area")
    @PreAuthorize(MANAGE) @GetMapping("/pos-terminals/{terminalId}/areas/{areaId}/activities")
    public ApiResponse<List<PosDtos.TerminalActivity>> terminalPolicies(@PathVariable UUID terminalId,@PathVariable UUID areaId) {
        return ApiResponse.success(service.terminalPolicies(terminalId,areaId));
    }
    @Operation(summary="Restrict activity operations on a POS",description="Omit permissions to inherit; explicit permissions must fit the activity, area and terminal caps. No override inherits automatically.")
    @PreAuthorize(MANAGE) @PutMapping("/pos-terminals/{terminalId}/areas/{areaId}/activities/{activityId}")
    public ApiResponse<PosDtos.TerminalActivity> terminalPolicy(@PathVariable UUID terminalId,@PathVariable UUID areaId,@PathVariable UUID activityId,@Valid @RequestBody PosDtos.AssignmentInput input) {
        return ApiResponse.success(service.terminalPolicy(terminalId,areaId,activityId,input));
    }
    @Operation(summary="Deny an activity on this POS and area, retaining an explicit disabled override")
    @PreAuthorize(MANAGE) @DeleteMapping("/pos-terminals/{terminalId}/areas/{areaId}/activities/{activityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTerminalPolicy(@PathVariable UUID terminalId,@PathVariable UUID areaId,@PathVariable UUID activityId) {
        service.terminalPolicy(terminalId,areaId,activityId,new PosDtos.AssignmentInput(null,false));
    }
    @Operation(summary="List activities readable in this POS session's selected area and cruise")
    @PreAuthorize(ACCESS) @GetMapping("/pos/activities")
    public ApiResponse<List<PosDtos.AvailableActivity>> available(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.available(token,employee));
    }
    @Operation(summary="Perform an authorized activity lifecycle action within the POS scope")
    @PreAuthorize(ACCESS) @PostMapping("/pos/activities/{activityId}/actions/{action}")
    public ApiResponse<ActivityView> lifecycle(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID activityId,@PathVariable String action) {
        return ApiResponse.success(service.lifecycle(token,employee,activityId,action));
    }
    @Operation(summary="List registrations for an activity accessible to the POS",description="Requires inherited ACTIVITY_REGISTRATION or ACTIVITY_ATTENDANCE, not ACTIVITY_READ alone.")
    @PreAuthorize(ACCESS) @GetMapping("/pos/activities/{activityId}/registrations")
    public ApiResponse<List<RegistrationView>> registrations(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID activityId) {
        return ApiResponse.success(service.registrations(token,employee,activityId));
    }
    @Operation(summary="Register a passenger for an authorized onboard activity",description="Reuses confirmed booking, same cruise, activity status, duplicates and capacity validation. This registration endpoint does not settle money.")
    @PreAuthorize(ACCESS) @PostMapping("/pos/activities/{activityId}/registrations") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationView> register(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID activityId,@Valid @RequestBody RegistrationRequest input) {
        return ApiResponse.success(service.register(token,employee,activityId,input));
    }
    @Operation(summary="Update a pending registration within the POS activity and cruise scope")
    @PreAuthorize(ACCESS) @PutMapping("/pos/activity-registrations/{id}")
    public ApiResponse<RegistrationView> updateRegistration(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID id,@Valid @RequestBody RegistrationUpdateRequest input) {
        return ApiResponse.success(service.changeRegistration(token,employee,id,"update",input,null));
    }
    @Operation(summary="Confirm or cancel an onboard registration within the POS scope")
    @PreAuthorize(ACCESS) @PostMapping("/pos/activity-registrations/{id}/actions/{action}")
    public ApiResponse<RegistrationView> registrationAction(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID id,@PathVariable String action,@RequestParam(required=false) @jakarta.validation.constraints.Size(max=500) String reason) {
        return ApiResponse.success(service.changeRegistration(token,employee,id,action,null,reason));
    }
    @Operation(summary="List attendance for an authorized activity in this POS cruise")
    @PreAuthorize(ACCESS) @GetMapping("/pos/activities/{activityId}/participations")
    public ApiResponse<List<ParticipationView>> participations(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID activityId) {
        return ApiResponse.success(service.participations(token,employee,activityId));
    }
    @Operation(summary="Check-in, checkout, complete or mark no-show for authorized onboard attendance")
    @PreAuthorize(ACCESS) @PostMapping("/pos/activity-participations/{id}/actions/{action}")
    public ApiResponse<ParticipationView> attendance(@RequestHeader("X-POS-Session") String token,@AuthenticationPrincipal CasmsPrincipal employee,@PathVariable UUID id,@PathVariable String action) {
        return ApiResponse.success(service.attendance(token,employee,id,action));
    }
}
