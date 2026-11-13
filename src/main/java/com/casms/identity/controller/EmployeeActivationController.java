package com.casms.identity.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.service.EmployeeAccountService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/employee-account")
public class EmployeeActivationController {
    private final EmployeeAccountService service;

    public record ActivationInput(@NotBlank @Size(max = 128) String token,
                                  @NotBlank @Size(min = 12, max = 72) String password) {}

    @PostMapping("/activate")
    public ApiResponse<Void> activate(@RequestBody @Valid ActivationInput input) {
        service.activate(input.token(), input.password());
        return ApiResponse.success("Tài khoản nhân viên đã được kích hoạt", null);
    }
}
