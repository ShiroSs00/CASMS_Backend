package com.casms.pos;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.controller.ServicepackageController.ServiceRequestView;
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
@Tag(name="Area services and POS",description="Employee JWT plus a device-bound POS session. POS permissions never become global employee permissions.")
public class PosController {
    private final PosService service;
    private final OnboardAllowanceService allowances;

    @Operation(summary="Read the shared internal spending allowance allocated to a booking")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/booking/{bookingId}/onboard-allowance")
    public ApiResponse<PosDtos.Allowance> allowance(@PathVariable UUID bookingId) {return ApiResponse.success(allowances.get(bookingId));}

    @Operation(summary="Allocate a shared booking allowance with optional individual passenger caps",
            description="Internal units only, not a deposit or payment. Creates/reuses the booking's onboard account and keeps allocation history. Omitted member caps stay unchanged; explicit caps replace active caps for those members. Allocation cannot fall below recorded usage.")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PutMapping("/booking/{bookingId}/onboard-allowance")
    public ApiResponse<PosDtos.Allowance> allocate(@PathVariable UUID bookingId,@Valid @RequestBody PosDtos.AllowanceInput input,
            @AuthenticationPrincipal CasmsPrincipal employee) {return ApiResponse.success(allowances.allocate(bookingId,input,employee));}

    @Operation(summary="List manually configured services and permissions for a physical area")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/ships/{shipId}/areas/{areaId}/services")
    public ApiResponse<List<PosDtos.AreaService>> areaServices(@PathVariable UUID shipId,@PathVariable UUID areaId) {
        return ApiResponse.success(service.assignments(shipId,areaId));
    }
    @Operation(summary="Assign a deck service to a published area and inherit its canonical permissions", description="Omit permissions to inherit the service policy; an explicit set may only narrow the policy. Source changes apply live to already linked areas and POS. Services are never created from model metadata.")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PutMapping("/ships/{shipId}/areas/{areaId}/services/{serviceId}")
    public ApiResponse<PosDtos.AreaService> assign(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID serviceId,
            @Valid @RequestBody PosDtos.AssignmentInput input) {return ApiResponse.success(service.assign(shipId,areaId,serviceId,input));}

    @Operation(summary="Remove the area-service link without deleting the service or sale history")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @DeleteMapping("/ships/{shipId}/areas/{areaId}/services/{serviceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID shipId,@PathVariable UUID areaId,@PathVariable UUID serviceId) {service.unassign(shipId,areaId,serviceId);}

    @Operation(summary="List configured POS terminals on a ship, without device credentials")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/ships/{shipId}/pos-terminals")
    public ApiResponse<List<PosDtos.Terminal>> terminals(@PathVariable UUID shipId) {return ApiResponse.success(service.terminals(shipId));}

    @Operation(summary="Create a POS terminal in a published physical area; return device secret once")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/ships/{shipId}/pos-terminals") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PosDtos.Credential> create(@PathVariable UUID shipId,@Valid @RequestBody PosDtos.TerminalInput input) {
        return ApiResponse.success(service.create(shipId,input));
    }
    @Operation(summary="Update POS permissions or enabled state and revoke all device sessions")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PutMapping("/pos-terminals/{terminalId}")
    public ApiResponse<PosDtos.Terminal> update(@PathVariable UUID terminalId,@Valid @RequestBody PosDtos.TerminalUpdate input) {
        return ApiResponse.success(service.update(terminalId,input));
    }
    @Operation(summary="Rotate the POS device secret, returning it once and revoking all sessions")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/pos-terminals/{terminalId}/credentials")
    public ApiResponse<PosDtos.Credential> rotate(@PathVariable UUID terminalId) {return ApiResponse.success(service.rotate(terminalId));}

    @Operation(summary="Open a POS session for an assigned employee, selected area and cruise",
            description="Requires POS_ACCESS, device secret, an enabled POS-area link and an active area/CruiseStaff assignment. areaId defaults to the original device area. cruiseId may be omitted only if exactly one current assignment matches. The assignment and permissions are checked on every subsequent request.")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/pos-terminals/{terminalId}/sessions") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PosDtos.Session> login(@PathVariable UUID terminalId,@Valid @RequestBody PosDtos.LoginInput input,
            @AuthenticationPrincipal CasmsPrincipal employee) {return ApiResponse.success(service.login(terminalId,input,employee));}

    @Operation(summary="Read the current POS context and effective permissions for each service")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/pos/session")
    public ApiResponse<PosDtos.Context> context(@RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.context(session,employee));
    }
    @Operation(summary="Revoke the current employee's POS session")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @DeleteMapping("/pos/session") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {service.logout(session,employee);}

    @Operation(summary="List active services readable through this POS's area and terminal permission intersection")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/pos/services")
    public ApiResponse<List<PosDtos.AvailableService>> services(@RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.available(session,employee));
    }
    @Operation(summary="List at most 200 recent service requests within this POS's services and ship")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/pos/requests")
    public ApiResponse<List<ServiceRequestView>> requests(@RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.requests(session,employee));
    }
    @Operation(summary="Assign or execute a service request through scoped POS permissions")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/pos/requests/{requestId}/actions/{action}")
    public ApiResponse<ServiceRequestView> action(@PathVariable UUID requestId,@PathVariable String action,
            @RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.action(session,employee,requestId,action));
    }
    @Operation(summary="Record onboard service usage against internal spending allowance",
            description="Requires scoped SERVICE_SALE. This records debt/history for settlement after the cruise, never cash collection. Existing confirmed booking, active account/member, allocated finite allowance, in-progress cruise, capacity and spending limits are validated. Price is read from the catalogue. Retries with the same terminal/idempotency key return the original entry.")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @PostMapping("/pos/sales") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PosDtos.Sale> sell(@Valid @RequestBody PosDtos.SaleInput input,@RequestHeader("X-POS-Session") String session,
            @AuthenticationPrincipal CasmsPrincipal employee) {return ApiResponse.success(service.sell(session,employee,input));}

    @Operation(summary="Read a recorded service charge belonging to this POS with scoped SERVICE_SALE")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/pos/sales/{saleId}")
    public ApiResponse<PosDtos.Sale> sale(@PathVariable UUID saleId,@RequestHeader("X-POS-Session") String session,
            @AuthenticationPrincipal CasmsPrincipal employee) {return ApiResponse.success(service.sale(session,employee,saleId));}

    @Operation(summary="Read allocated internal allowance, remaining allowance and usage history",
            description="Requires scoped SERVICE_SALE and an active spending membership. Real payments are excluded from allowance calculations. Records remain available for settlement after the cruise.")
    @PreAuthorize("hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked")
    @GetMapping("/pos/accounts/{accountId}/passengers/{bookingPassengerId}/statement")
    public ApiResponse<PosDtos.Statement> statement(@PathVariable UUID accountId,@PathVariable UUID bookingPassengerId,
            @RequestHeader("X-POS-Session") String session,@AuthenticationPrincipal CasmsPrincipal employee) {
        return ApiResponse.success(service.statement(session,employee,accountId,bookingPassengerId));
    }
}
