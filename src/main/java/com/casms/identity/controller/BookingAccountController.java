package com.casms.identity.controller;

import static com.casms.notification.service.BookingEmailService.MAX_ACTIVATION_PASSWORD_LENGTH;
import static com.casms.notification.service.BookingEmailService.MAX_ACTIVATION_TOKEN_LENGTH;
import static com.casms.notification.service.BookingEmailService.MIN_ACTIVATION_PASSWORD_LENGTH;

import com.casms.common.response.ApiResponse;
import com.casms.notification.service.BookingEmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/booking-account")
@RequiredArgsConstructor
public class BookingAccountController {
    private final BookingEmailService service;

    public record ActivateRequest(@NotBlank @Size(max = MAX_ACTIVATION_TOKEN_LENGTH) String token,
                                  @NotBlank @Size(min = MIN_ACTIVATION_PASSWORD_LENGTH,
                                          max = MAX_ACTIVATION_PASSWORD_LENGTH) String password) {}

    @PostMapping("/activate")
    public ApiResponse<Void> activate(@RequestBody @Valid ActivateRequest request) {
        service.activate(request.token(), request.password());
        return ApiResponse.success("Password set; sign in with your booking account", null);
    }
}
