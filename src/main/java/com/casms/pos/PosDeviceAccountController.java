package com.casms.pos;

import com.casms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/pos-terminals/{terminalId}/account")
public class PosDeviceAccountController {
    private final PosDeviceAccountService service;
    @Operation(summary="Read a terminal's dedicated POS account")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @GetMapping
    public ApiResponse<PosDtos.DeviceAccount> account(@PathVariable UUID terminalId) {return ApiResponse.success(service.account(terminalId));}
    @Operation(summary="Bind or replace a dedicated POS account",description="Requires an ACTIVE account with only role POS. One account identifies one terminal. Binding revokes sessions; subsequent employee logins require deviceAccessToken from this account, with the employee JWT in Authorization. Device secret cannot bypass a bound account.")
    @PreAuthorize("hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked")
    @PutMapping
    public ApiResponse<PosDtos.DeviceAccount> bind(@PathVariable UUID terminalId,@Valid @RequestBody PosDtos.DeviceAccountInput input) {
        return ApiResponse.success(service.bind(terminalId,input.userId()));
    }
}
