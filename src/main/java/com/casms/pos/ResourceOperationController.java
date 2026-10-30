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
import java.util.*;
import static com.casms.pos.PosPermission.ResourceType;

@RestController @RequiredArgsConstructor
@Tag(name="Resource operation permissions",description="Classified scoped operations inherited live by areas and POS; no global role grants")
@PreAuthorize("principal.enabled and principal.accountNonLocked")
public class ResourceOperationController {
    private final ResourceOperationService service;
    private static final String MANAGER="hasRole('ACTIVITY_SERVICE_MANAGER') and principal.enabled and principal.accountNonLocked";
    @Operation(summary="List assignable operations classified by resource type and operation category")
    @PreAuthorize("hasAnyAuthority('SERVICE_READ','ACTIVITY_READ','POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/operation-permissions")
    public ApiResponse<List<PosDtos.PermissionDefinition>> definitions(@RequestParam(required=false) ResourceType resourceType) {
        return ApiResponse.success(service.definitions(resourceType));
    }
    @Operation(summary="Read a service's canonical operation policy; unconfigured means deny")
    @PreAuthorize("hasAnyAuthority('SERVICE_READ','POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/servicepackage/services/{id}/permissions")
    public ApiResponse<PosDtos.ResourcePolicy> servicePermissions(@PathVariable UUID id) {return ApiResponse.success(service.get(ResourceType.SERVICE,id));}

    @Operation(summary="Replace a service's operation permissions",description="Only the activity/service manager can configure source permissions. Existing linked areas immediately inherit changes, subject to their restrictions and terminal caps.")
    @PreAuthorize(MANAGER+" and hasAuthority('SERVICE_UPDATE')")
    @PutMapping("/servicepackage/services/{id}/permissions")
    public ApiResponse<PosDtos.ResourcePolicy> setServicePermissions(@PathVariable UUID id,@Valid @RequestBody PosDtos.PermissionInput input,@AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.put(ResourceType.SERVICE,id,input.permissions(),actor.getUserId()));
    }
    @Operation(summary="Clear service operation permissions without deleting the service or its history")
    @PreAuthorize(MANAGER+" and hasAuthority('SERVICE_UPDATE')")
    @DeleteMapping("/servicepackage/services/{id}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearServicePermissions(@PathVariable UUID id,@AuthenticationPrincipal CasmsPrincipal actor) {service.put(ResourceType.SERVICE,id,Set.of(),actor.getUserId());}

    @Operation(summary="Read an onboard activity's canonical operation policy; unconfigured means deny")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_READ','POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/activities/{id}/permissions")
    public ApiResponse<PosDtos.ResourcePolicy> activityPermissions(@PathVariable UUID id) {return ApiResponse.success(service.get(ResourceType.ACTIVITY,id));}
    @Operation(summary="Replace onboard activity operation permissions",description="Only the activity/service manager may configure activity operations; service permissions are rejected.")
    @PreAuthorize(MANAGER+" and hasAuthority('ACTIVITY_UPDATE')")
    @PutMapping("/activities/{id}/permissions")
    public ApiResponse<PosDtos.ResourcePolicy> setActivityPermissions(@PathVariable UUID id,@Valid @RequestBody PosDtos.PermissionInput input,@AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.put(ResourceType.ACTIVITY,id,input.permissions(),actor.getUserId()));
    }
    @Operation(summary="Clear activity permissions without deleting activity registrations or attendance history")
    @PreAuthorize(MANAGER+" and hasAuthority('ACTIVITY_UPDATE')")
    @DeleteMapping("/activities/{id}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearActivityPermissions(@PathVariable UUID id,@AuthenticationPrincipal CasmsPrincipal actor) {service.put(ResourceType.ACTIVITY,id,Set.of(),actor.getUserId());}
}
