package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.BleAnchorCreateRequest;
import com.casms.ship.dto.request.BleAnchorUpdateRequest;
import com.casms.ship.dto.response.BleAnchorResponse;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.service.BleAnchorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "BLE Anchor Management", description = "Quản lý receiver/beacon BLE, vị trí mô hình và trạng thái lắp đặt")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ships/{shipId}/ble-anchors")
public class BleAnchorController {
    private final BleAnchorService service;

    @Operation(summary = "Tạo BLE anchor/receiver")
    @PreAuthorize("hasAuthority('BLE_ANCHOR_CREATE')")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BleAnchorResponse> create(@PathVariable UUID shipId, @Valid @RequestBody BleAnchorCreateRequest request) {
        return ApiResponse.success(service.create(shipId, request));
    }

    @Operation(summary = "Danh sách BLE anchor/receiver")
    @PreAuthorize("hasAuthority('BLE_ANCHOR_READ')")
    @GetMapping
    public ApiResponse<List<BleAnchorResponse>> list(@PathVariable UUID shipId, @RequestParam(required = false) UUID deckId) {
        return ApiResponse.success(service.list(shipId, deckId));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_READ')")
    @GetMapping("/{anchorId}")
    public ApiResponse<BleAnchorResponse> detail(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        return ApiResponse.success(service.detail(shipId, anchorId));
    }

    @Operation(summary = "Cập nhật định danh, vị trí hoặc hiệu chuẩn BLE")
    @PreAuthorize("hasAuthority('BLE_ANCHOR_UPDATE')")
    @PatchMapping("/{anchorId}")
    public ApiResponse<BleAnchorResponse> update(@PathVariable UUID shipId, @PathVariable UUID anchorId,
                                                  @Valid @RequestBody BleAnchorUpdateRequest request) {
        return ApiResponse.success(service.update(shipId, anchorId, request));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_ACTIVATE')")
    @PostMapping("/{anchorId}/activate")
    public ApiResponse<BleAnchorResponse> activate(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        return ApiResponse.success(service.setStatus(shipId, anchorId, LocationDeviceStatus.ACTIVE));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_DEACTIVATE')")
    @PostMapping("/{anchorId}/deactivate")
    public ApiResponse<BleAnchorResponse> deactivate(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        return ApiResponse.success(service.setStatus(shipId, anchorId, LocationDeviceStatus.INACTIVE));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_INSTALL')")
    @PostMapping("/{anchorId}/installation-confirmation")
    public ApiResponse<BleAnchorResponse> confirmInstallation(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        return ApiResponse.success(service.confirmInstallation(shipId, anchorId));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_RETIRE')")
    @PostMapping("/{anchorId}/retire")
    public ApiResponse<BleAnchorResponse> retire(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        return ApiResponse.success(service.retire(shipId, anchorId));
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_VALIDATE')")
    @PostMapping("/{anchorId}/validate")
    public ApiResponse<Void> validate(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        service.validate(shipId, anchorId);
        return ApiResponse.success("BLE anchor configuration is valid", null);
    }

    @PreAuthorize("hasAuthority('BLE_ANCHOR_DELETE')")
    @DeleteMapping("/{anchorId}/unused") @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(@PathVariable UUID shipId, @PathVariable UUID anchorId) {
        service.deleteUnused(shipId, anchorId);
        return ApiResponse.success("Unused BLE anchor deleted", null);
    }
}
