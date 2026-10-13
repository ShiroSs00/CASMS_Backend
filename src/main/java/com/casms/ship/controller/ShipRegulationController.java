package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.RegulationPrecheckRequest;
import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.dto.response.RegulationAssignmentSnapshotResponse;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.dto.response.ShipRegulationResponse;
import com.casms.ship.service.ShipRegulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ship Regulation Management", description = "Quản lý và áp dụng quy định vận hành cho tàu du lịch")
@RestController
@RequestMapping("/ships/{shipId}/regulations")
@RequiredArgsConstructor
public class ShipRegulationController {

    private final ShipRegulationService shipRegulationService;

    @Operation(summary = "Gán quy định cho tàu", description = "Áp dụng một quy định vận hành cụ thể vào tàu du lịch")
    @PostMapping
    public ResponseEntity<ApiResponse<ShipRegulationResponse>> assign(
            @PathVariable UUID shipId,
            @Valid @RequestBody ShipRegulationRequest request) {

        request.setShipId(shipId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shipRegulationService.assign(request)));
    }

    @Operation(summary = "Kiểm tra xung đột trước khi gán cho tàu", description = "Precheck xung đột quy định trước khi thực hiện gán cho tàu")
    @PostMapping("/precheck")
    public ResponseEntity<ApiResponse<RegulationConflictResponse>> precheck(
            @PathVariable UUID shipId,
            @Valid @RequestBody RegulationPrecheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shipRegulationService.precheck(shipId, request.getRegulationId())));
    }

    @Operation(summary = "Danh sách quy định của tàu", description = "Lấy danh sách tất cả các quy định đang áp dụng cho tàu")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipRegulationResponse>>> list(
            @PathVariable UUID shipId) {
        return ResponseEntity.ok(ApiResponse.success(shipRegulationService.list(shipId)));
    }

    @Operation(summary = "Snapshot quy định hiệu lực của tàu", description = "Lấy snapshot các quy định hiệu lực trực tiếp và kế thừa của tàu")
    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<RegulationAssignmentSnapshotResponse>> getEffectiveSnapshot(
            @PathVariable UUID shipId,
            @RequestParam(required = false) Instant at) {
        return ResponseEntity.ok(ApiResponse.success(
                shipRegulationService.getEffectiveSnapshot(shipId, at)));
    }

    @Operation(summary = "Gỡ quy định khỏi tàu", description = "Gỡ bỏ áp dụng quy định khỏi tàu")
    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID shipId,
            @PathVariable UUID regulationId) {
        shipRegulationService.remove(shipId, regulationId);
        return ResponseEntity.noContent().build();
    }
}
