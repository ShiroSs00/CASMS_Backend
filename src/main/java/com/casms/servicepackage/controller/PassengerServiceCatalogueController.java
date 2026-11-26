package com.casms.servicepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.service.PassengerServiceCatalogue;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PassengerServiceCatalogueController {
    private final PassengerServiceCatalogue catalogue;

    @GetMapping("/servicepackage/passenger/{bookingPassengerId}/catalogue")
    @PreAuthorize("hasRole('PASSENGER') and hasAnyAuthority('SERVICE_REQUEST_CREATE','SERVICE_REQUEST_READ')"
            + " and principal.enabled and principal.accountNonLocked")
    public ApiResponse<PassengerServiceCatalogue.Catalogue> catalogue(
            @PathVariable UUID bookingPassengerId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(catalogue.list(bookingPassengerId, actor.getUserId()));
    }
}
