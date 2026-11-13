package com.casms.identity.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.enums.Role;
import com.casms.identity.service.EmployeeAccountService;
import com.casms.identity.service.EmployeeAccountService.CreateInput;
import com.casms.identity.service.EmployeeAccountService.EmployeeView;
import com.casms.identity.service.EmployeeAccountService.UpdateInput;
import com.casms.security.principal.CasmsPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/employees")
public class EmployeeAccountController {
    private final EmployeeAccountService service;

    public record InactivateInput(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping("/assignable-roles")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_CREATE')")
    public ApiResponse<List<Role>> assignableRoles(@AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.assignableRoles(actor));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_CREATE')")
    public ApiResponse<EmployeeView> create(
            @RequestBody @Valid CreateInput input, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.create(input, actor));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_READ')")
    public ApiResponse<List<EmployeeView>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.list(page, size, actor));
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_READ')")
    public ApiResponse<EmployeeView> detail(
            @PathVariable UUID employeeId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.profile(employeeId, actor));
    }

    @PutMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_UPDATE')")
    public ApiResponse<EmployeeView> update(
            @PathVariable UUID employeeId, @RequestBody @Valid UpdateInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.update(employeeId, input, actor));
    }

    @PostMapping("/{employeeId}/inactivate")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_STATUS_CHANGE')")
    public ApiResponse<EmployeeView> inactivate(
            @PathVariable UUID employeeId, @RequestBody @Valid InactivateInput input,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.inactivate(employeeId, input.reason(), actor));
    }

    @PostMapping("/{employeeId}/activation-email/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_UPDATE')")
    public void retryActivationEmail(
            @PathVariable UUID employeeId, @AuthenticationPrincipal CasmsPrincipal actor) {
        service.retryActivationEmail(employeeId, actor);
    }
}
