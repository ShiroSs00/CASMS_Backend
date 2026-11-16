package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.dto.response.ShipTypeResponse;
import com.casms.ship.service.ShipTypeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.web.PageableDefault;
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

    public record StoredDesign(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,80}") String modelKey,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,80}") String assetVersion) {}

    @PostMapping("/from-stored")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipTypeResponse> createStored(@Valid @RequestBody StoredDesign design) {
        return ApiResponse.success(service.createFromStored(design.modelKey(), design.assetVersion()));
    }

    @PutMapping("/{typeId}/model")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShipTypeResponse> updateStored(@PathVariable UUID typeId,
            @Valid @RequestBody StoredDesign design) {
        return ApiResponse.success(service.replaceStoredModel(typeId, design.modelKey(), design.assetVersion()));
    }

    @GetMapping("/{typeId}/ships")
    @PreAuthorize("hasAnyRole('ADMIN','SHIP_MANAGER') and hasAuthority('SHIP_READ')")
    public ApiResponse<Page<ShipResponse>> ships(@PathVariable UUID typeId,
            @PageableDefault(size = 20, sort = "shipCode") Pageable pageable, Authentication actor) {
        return ApiResponse.success(service.listShips(typeId, pageable, isAdmin(actor)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SHIP_MANAGER')")
    public ApiResponse<Page<ShipTypeResponse>> list(@PageableDefault(size = 20, sort = "code") Pageable pageable,
            @RequestParam(required = false) TypeStatus status,
            @RequestParam(name = "q", required = false) String search, Authentication actor) {
        return ApiResponse.success(service.list(pageable, isAdmin(actor),
                status == null ? null : status == TypeStatus.ACTIVE, search));
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
            @RequestHeader("Idempotency-Key") UUID requestKey,
            @Valid @RequestBody ShipFromTypeRequest request, Authentication actor) {
        return ApiResponse.success(service.createShip(typeId, request, actor.getName(), requestKey));
    }

    @GetMapping("/{typeId}/ships/provisioning/{requestKey}")
    @PreAuthorize("hasRole('SHIP_MANAGER')")
    public ApiResponse<ShipResponse> provisioningResult(
            @PathVariable UUID typeId, @PathVariable UUID requestKey) {
        return ApiResponse.success(service.provisioningResult(typeId, requestKey));
    }

    private static boolean isAdmin(Authentication actor) {
        return actor.getAuthorities().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()));
    }

    public enum TypeStatus { ACTIVE, INACTIVE }
}
