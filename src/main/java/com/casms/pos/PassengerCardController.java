package com.casms.pos;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PassengerCardController {
    private static final String CARD_STAFF = "hasAuthority('POS_ACCESS') and hasAuthority('CHECKIN_BOARDING')"
            + " and principal.enabled and principal.accountNonLocked";

    private final PassengerCardService service;

    public record ClaimRequest(@NotBlank @Size(max = 43) String code) {}
    public record ConfirmRequest(
            @NotBlank @Size(max = 43) String code,
            @NotBlank @Size(max = 64) String cardIdentifier) {}
    public record LookupRequest(@NotBlank @Size(max = 64) String cardIdentifier) {}

    @PostMapping("/checkin/passenger/{passengerId}/card-claim")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('CHECKIN_READ')")
    public ApiResponse<PassengerCardService.CardClaim> issue(
            @PathVariable UUID passengerId, @AuthenticationPrincipal CasmsPrincipal customer) {
        return ApiResponse.success(service.issue(passengerId, customer.getUserId()));
    }

    @PostMapping("/pos/passenger-cards/preview")
    @PreAuthorize(CARD_STAFF)
    public ApiResponse<PassengerCardService.PassengerPreview> preview(
            @RequestHeader("X-POS-Session") String token, @AuthenticationPrincipal CasmsPrincipal employee,
            @Valid @RequestBody ClaimRequest input) {
        return ApiResponse.success(service.preview(token, employee, input.code()));
    }

    @PostMapping("/pos/passenger-cards/confirm")
    @PreAuthorize(CARD_STAFF)
    public ApiResponse<PassengerCardService.CardAssignment> confirm(
            @RequestHeader("X-POS-Session") String token, @AuthenticationPrincipal CasmsPrincipal employee,
            @Valid @RequestBody ConfirmRequest input) {
        return ApiResponse.success(service.confirm(token, employee, input.code(), input.cardIdentifier()));
    }

    @PostMapping("/pos/passenger-cards/lookup")
    @PreAuthorize(CARD_STAFF)
    public ApiResponse<PassengerCardService.CardAssignment> lookup(
            @RequestHeader("X-POS-Session") String token, @AuthenticationPrincipal CasmsPrincipal employee,
            @Valid @RequestBody LookupRequest input) {
        return ApiResponse.success(service.lookup(token, employee, input.cardIdentifier()));
    }
}
