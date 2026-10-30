package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipModelCreateRequest;
import com.casms.ship.dto.request.ShipModelObjectCreateRequest;
import com.casms.ship.dto.request.ShipModelAssetRegisterRequest;
import com.casms.ship.dto.request.ShipModelProvisionRequest;
import com.casms.ship.dto.request.ShipModelCabinMappingRequest;
import com.casms.ship.dto.request.ShipModelDeckMappingRequest;
import com.casms.ship.dto.response.Ship3dConfigurationResponse;
import com.casms.ship.dto.response.ShipModelAssetResponse;
import com.casms.ship.dto.response.ShipModelAssetUploadResponse;
import com.casms.ship.dto.response.ShipModelManifestRegistrationResponse;
import com.casms.ship.dto.response.ShipModelObjectResponse;
import com.casms.ship.dto.response.ShipModelResponse;
import com.casms.ship.dto.response.ModelImportJobResponse;
import com.casms.ship.dto.response.ModelImportIssueResponse;
import com.casms.ship.service.ShipModelImportService;
import com.casms.ship.service.ShipModelAssetService;
import com.casms.ship.service.ShipModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Ship 3D Configuration", description = "Asset metadata, model node mappings and complete 3D configuration")
@RestController
@RequiredArgsConstructor
@Validated
public class ShipModelController {
    private final ShipModelService service;
    private final ShipModelAssetService assetService;
    private final ShipModelImportService importService;

    @Operation(summary = "Import physical decks, cabins and areas from a registered model manifest",
            description = "Physical area nodes are stored as FACILITY mappings linked to decks, including spatial metadata. Business service nodes do not create catalogue services or deck-service assignments.")
    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PostMapping("/ships/{shipId}/models/{modelId}/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModelImportJobResponse> importStructure(@PathVariable UUID shipId, @PathVariable UUID modelId,
            org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(importService.importModel(shipId, modelId,
                authentication == null ? null : authentication.getName()));
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}/imports")
    public ApiResponse<List<ModelImportJobResponse>> importHistory(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        return ApiResponse.success(importService.history(shipId, modelId));
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/imports/{jobId}")
    public ApiResponse<ModelImportJobResponse> importJob(@PathVariable UUID shipId, @PathVariable UUID jobId) {
        return ApiResponse.success(importService.job(shipId, jobId));
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/imports/{jobId}/issues")
    public ApiResponse<List<ModelImportIssueResponse>> importIssues(@PathVariable UUID shipId, @PathVariable UUID jobId) {
        return ApiResponse.success(importService.issues(shipId, jobId));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_CREATE') and hasAuthority('SHIP_CREATE')")
    @GetMapping("/ship-model-assets")
    public ApiResponse<List<com.casms.ship.service.ShipModelAssetStore.StoredBundle>> storedAssets() {
        return ApiResponse.success(assetService.listStoredBundles());
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_CREATE') and hasAuthority('SHIP_CREATE')")
    @GetMapping("/ship-model-assets/{modelKey}/{assetVersion}/preview")
    public ApiResponse<com.fasterxml.jackson.databind.JsonNode> previewStoredAsset(@PathVariable String modelKey,@PathVariable String assetVersion) {
        return ApiResponse.success(assetService.previewStoredBundle(modelKey,assetVersion));
    }

    @Operation(summary = "Upload and validate a versioned ship model asset ZIP into PostgreSQL",description="At most two stored bundles may contain byte-identical full-model GLB content, even under different keys or versions. Existing uploads count toward this limit; invalid or rolled-back uploads do not. Creating ships from stored models does not upload another copy.")
    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PostMapping(value = "/ship-model-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipModelAssetUploadResponse> uploadAssets(@RequestParam String modelKey,
            @RequestParam String assetVersion,
            @RequestPart("bundle") MultipartFile bundle) {
        return ApiResponse.success(assetService.uploadBundle(modelKey, assetVersion, bundle));
    }

    @Operation(summary = "Register a versioned asset bundle already stored in PostgreSQL")
    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PostMapping("/ships/{shipId}/models") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, UUID>> create(@PathVariable UUID shipId,
            @Valid @RequestBody ShipModelAssetRegisterRequest request) {
        return ApiResponse.success(Map.of("id", assetService.register(shipId, request)));
    }

    @Operation(summary = "Create a new ship from an uploaded reusable model",description="Every call creates an independent ship with its own code. An optional body supplies only metadata absent from the model; optional missing data is omitted. Source uploads and creation timestamps are generated by the server. Use the existing ship-model registration API to attach revisions to a specific ship.")
    @PreAuthorize("hasAuthority('SHIP_MODEL_CREATE') and hasAuthority('SHIP_CREATE')")
    @PostMapping("/ship-model-assets/{modelKey}/{assetVersion}/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipModelManifestRegistrationResponse> registerFromManifest(
            @PathVariable String modelKey, @PathVariable String assetVersion,
            @Valid @RequestBody(required=false) ShipModelProvisionRequest supplements,
            org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(assetService.registerFromManifest(modelKey, assetVersion, supplements==null?null:supplements.supplements(), authentication));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_READ')")
    @GetMapping("/ships/{shipId}/models")
    public ApiResponse<List<ShipModelResponse>> list(@PathVariable UUID shipId) { return ApiResponse.success(service.list(shipId)); }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}")
    public ApiResponse<ShipModelAssetResponse> detail(@PathVariable UUID shipId, @PathVariable UUID modelId,
            HttpServletRequest request) {
        return ApiResponse.success(assetService.detail(shipId, modelId, request.getContextPath()));
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/model")
    public ApiResponse<ShipModelAssetResponse> active(@PathVariable UUID shipId, HttpServletRequest request) {
        return ApiResponse.success(assetService.active(shipId, request.getContextPath()));
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}/deck-mappings")
    public ApiResponse<List<ShipModelDeckMappingRequest>> deckMappings(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        return ApiResponse.success(assetService.deckMappings(shipId, modelId));
    }

    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PutMapping("/ships/{shipId}/models/{modelId}/deck-mappings")
    public ApiResponse<String> replaceDeckMappings(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @RequestBody @Size(max = 100) List<@NotNull @Valid ShipModelDeckMappingRequest> mappings) {
        assetService.replaceDeckMappings(shipId, modelId, mappings);
        return ApiResponse.success("Deck mappings replaced", "Deck mappings replaced");
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}/cabin-mappings")
    public ApiResponse<List<ShipModelCabinMappingRequest>> cabinMappings(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        return ApiResponse.success(assetService.cabinMappings(shipId, modelId));
    }

    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PutMapping("/ships/{shipId}/models/{modelId}/cabin-mappings")
    public ApiResponse<String> replaceCabinMappings(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @RequestBody @Size(max = 5000) List<@NotNull @Valid ShipModelCabinMappingRequest> mappings) {
        assetService.replaceCabinMappings(shipId, modelId, mappings);
        return ApiResponse.success("Cabin mappings replaced", "Cabin mappings replaced");
    }

    @PreAuthorize("hasAuthority('SHIP_UPDATE')")
    @PostMapping("/ships/{shipId}/models/{modelId}/activate")
    public ApiResponse<String> activate(@PathVariable UUID shipId, @PathVariable UUID modelId) {
        assetService.activate(shipId, modelId);
        return ApiResponse.success("Model activated", "Model activated");
    }

    @PreAuthorize("hasAuthority('SHIP_READ')")
    @GetMapping("/ships/{shipId}/models/{modelId}/assets/{*assetPath}")
    public ResponseEntity<StreamingResponseBody> asset(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @PathVariable String assetPath) {
        String name = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        var metadata = assetService.asset(shipId, modelId, name);
        MediaType mediaType = name.endsWith(".glb")
                ? MediaType.parseMediaType("model/gltf-binary") : MediaType.APPLICATION_JSON;
        StreamingResponseBody body = output -> assetService.writeAsset(shipId, modelId, name, output);
        return ResponseEntity.ok().contentType(mediaType).contentLength(metadata.size())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(body);
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_UPDATE')")
    @PutMapping("/ships/{shipId}/models/{modelId}")
    public ApiResponse<ShipModelResponse> update(@PathVariable UUID shipId, @PathVariable UUID modelId,
            @Valid @RequestBody ShipModelCreateRequest request) {
        return ApiResponse.success(service.update(shipId, modelId, request));
    }

    @PreAuthorize("hasAuthority('SHIP_MODEL_CREATE')")
    @PostMapping("/ships/{shipId}/models/metadata") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipModelResponse> createMetadataReference(@PathVariable UUID shipId,
            @Valid @RequestBody ShipModelCreateRequest request) {
        return ApiResponse.success(service.create(shipId, request));
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

    @Operation(summary = "Get ship, model, deck, cabin, facility and BLE anchor configuration for 3D clients",
            description = "Each deck includes physical facilities with transforms, dimensions and original model metadata. Catalogue services are managed separately.")
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
