package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseWorkHandoffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruise-work")
public class CruiseWorkHandoffController {
    private final CruiseWorkHandoffService service;

    public record Send(@NotBlank @Size(max = 2000) String request) {}
    public record Reply(@NotNull CruiseWorkHandoffService.Action action, @NotNull Long revision,
                        @Size(max = 2000) String response) {}

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','SHIP_MANAGER')")
    public ApiResponse<List<CruiseWorkHandoffService.Task>> inbox(@AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.inbox(actor));
    }

    @GetMapping("/{cruiseId}")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_PREPARATION_SUBMIT')")
    public ApiResponse<CruiseWorkHandoffService.Panel> get(
            @PathVariable UUID cruiseId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.forCoordinator(cruiseId, actor));
    }

    @GetMapping("/{cruiseId}/program-context")
    @PreAuthorize("@cruiseWorkHandoffService.mayReadPrograms(#cruiseId, authentication.principal)")
    public ApiResponse<CruiseWorkHandoffService.ProgramContext> programContext(
            @PathVariable UUID cruiseId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.programContext(cruiseId, actor));
    }

    @PostMapping("/{cruiseId}/{group}")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_PREPARATION_SUBMIT')")
    public ApiResponse<CruiseWorkHandoffService.Panel> send(
            @PathVariable UUID cruiseId, @PathVariable CruiseWorkHandoffService.Group group,
            @RequestBody @Valid Send input, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.send(cruiseId, group, input.request().strip(), actor));
    }

    @PutMapping("/{cruiseId}/{group}")
    @PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','SHIP_MANAGER')")
    public ApiResponse<CruiseWorkHandoffService.Task> respond(
            @PathVariable UUID cruiseId, @PathVariable CruiseWorkHandoffService.Group group,
            @RequestBody @Valid Reply input, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.respond(cruiseId, group, input.action(), input.revision(),
                input.response() == null ? null : input.response().strip(), actor));
    }
}
