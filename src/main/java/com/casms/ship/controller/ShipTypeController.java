package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.dto.response.ShipTypeResponse;
import com.casms.ship.service.ShipTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/ship-types")
@RequiredArgsConstructor
public class ShipTypeController {
    private final ShipTypeService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipTypeResponse> create(@RequestParam String modelKey,
            @RequestPart MultipartFile bundle) {
        return ApiResponse.success(service.create(modelKey, bundle));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SHIP_MANAGER')")
    public ApiResponse<Page<ShipTypeResponse>> list(Pageable pageable, Authentication actor) {
        return ApiResponse.success(service.list(pageable, isAdmin(actor)));
    }

    @GetMapping("/{typeId}")
    @PreAuthorize("hasAnyRole('ADMIN','SHIP_MANAGER')")
    public ApiResponse<ShipTypeResponse> detail(@PathVariable UUID typeId, Authentication actor) {
        return ApiResponse.success(service.detail(typeId, isAdmin(actor)));
    }

    @PutMapping(value = "/{typeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShipTypeResponse> update(@PathVariable UUID typeId,
            @RequestParam String modelKey, @RequestPart MultipartFile bundle) {
        return ApiResponse.success(service.replaceModel(typeId, modelKey, bundle));
    }

    @PatchMapping("/{typeId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShipTypeResponse> status(@PathVariable UUID typeId,
            @RequestParam TypeStatus status) {
        return ApiResponse.success(service.setActive(typeId, status == TypeStatus.ACTIVE));
    }

    @DeleteMapping("/{typeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID typeId) {
        service.delete(typeId);
    }

    @PostMapping("/{typeId}/ships")
    @PreAuthorize("hasRole('SHIP_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipResponse> createShip(@PathVariable UUID typeId,
            @Valid @RequestBody ShipFromTypeRequest request, Authentication actor) {
        return ApiResponse.success(service.createShip(typeId, request, actor.getName()));
    }

    private static boolean isAdmin(Authentication actor) {
        return actor.getAuthorities().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()));
    }

    public enum TypeStatus { ACTIVE, INACTIVE }
}
