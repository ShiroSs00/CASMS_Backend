package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipModelCreateRequest;
import com.casms.ship.dto.request.ShipModelObjectCreateRequest;
import com.casms.ship.dto.response.Ship3dConfigurationResponse;
import com.casms.ship.dto.response.ShipModelObjectResponse;
import com.casms.ship.dto.response.ShipModelResponse;
import com.casms.ship.service.ShipModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ship 3D Configuration", description = "Asset metadata, model node mappings and complete 3D configuration")
@RestController
@RequiredArgsConstructor
public class ShipModelController {
    private final ShipModelService service;

    @Operation(summary = "Register a ship model asset reference")
    @PreAuthorize("hasAuthority('SHIP_MODEL_CREATE')")
    @PostMapping("/ships/{shipId}/models") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipModelResponse> create(@PathVariable UUID shipId, @Valid @RequestBody ShipModelCreateRequest request) {
        return ApiResponse.success(service.create(shipId, request));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_READ')")
    @GetMapping("/ships/{shipId}/models")
    public ApiResponse<List<ShipModelResponse>> list(@PathVariable UUID shipId) { return ApiResponse.success(service.list(shipId)); }

    @PreAuthorize("hasAuthority('SHIP_MODEL_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}")
    public ApiResponse<ShipModelResponse> detail(@PathVariable UUID shipId, @PathVariable UUID modelId) { return ApiResponse.success(service.detail(shipId, modelId)); }

    @PreAuthorize("hasAuthority('SHIP_MODEL_UPDATE')")
    @PutMapping("/ships/{shipId}/models/{modelId}")
    public ApiResponse<ShipModelResponse> update(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @Valid @RequestBody ShipModelCreateRequest request) {
        return ApiResponse.success(service.update(shipId, modelId, request));
    }

    @Operation(summary = "Validate model metadata and publish an immutable READY version")
    @PreAuthorize("hasAuthority('SHIP_MODEL_PUBLISH')")
    @PostMapping("/ships/{shipId}/models/{modelId}/ready")
    public ApiResponse<ShipModelResponse> ready(@PathVariable UUID shipId, @PathVariable UUID modelId) { return ApiResponse.success(service.setReady(shipId, modelId)); }

    @PreAuthorize("hasAuthority('SHIP_MODEL_PUBLISH')")
    @PostMapping("/ships/{shipId}/models/{modelId}/archive")
    public ApiResponse<ShipModelResponse> archive(@PathVariable UUID shipId, @PathVariable UUID modelId) { return ApiResponse.success(service.archive(shipId, modelId)); }

    @PreAuthorize("hasAuthority('SHIP_MODEL_DELETE')")
    @DeleteMapping("/ships/{shipId}/models/{modelId}/unused") @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        service.deleteDraft(shipId, modelId);
        return ApiResponse.success("Draft model deleted", null);
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_OBJECT_CREATE')")
    @PostMapping("/ships/{shipId}/models/{modelId}/objects") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipModelObjectResponse> createObject(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @Valid @RequestBody ShipModelObjectCreateRequest request) {
        return ApiResponse.success(service.createObject(shipId, modelId, request));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}/objects")
    public ApiResponse<List<ShipModelObjectResponse>> listObjects(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        return ApiResponse.success(service.listObjects(shipId, modelId));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_OBJECT_UPDATE')")
    @PutMapping("/ships/{shipId}/models/{modelId}/objects/{objectId}")
    public ApiResponse<ShipModelObjectResponse> updateObject(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @PathVariable UUID objectId, @Valid @RequestBody ShipModelObjectCreateRequest request) {
        return ApiResponse.success(service.updateObject(shipId, modelId, objectId, request));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_OBJECT_DELETE')")
    @DeleteMapping("/ships/{shipId}/models/{modelId}/objects/{objectId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteObject(@PathVariable UUID shipId, @PathVariable UUID modelId, @PathVariable UUID objectId) {
        service.deleteObject(shipId, modelId, objectId);
        return ApiResponse.success("Model object mapping deleted", null);
    }

    @Operation(summary = "Get complete ship, model, deck, cabin and BLE anchor configuration for 3D clients")
    @PreAuthorize("hasAuthority('SHIP_CONFIGURATION_READ')")
    @GetMapping("/ships/{shipId}/3d-configuration")
    public ApiResponse<Ship3dConfigurationResponse> configuration(@PathVariable UUID shipId) {
        return ApiResponse.success(service.configuration(shipId));
    }

    @Operation(summary = "Validate the ship's 3D configuration before clients use it")
    @PreAuthorize("hasAuthority('SHIP_CONFIGURATION_VALIDATE')")
    @PostMapping("/ships/{shipId}/3d-configuration/validate")
    public ApiResponse<Void> validate(@PathVariable UUID shipId) {
        service.validateConfiguration(shipId);
        return ApiResponse.success("3D configuration is valid", null);
    }
}
