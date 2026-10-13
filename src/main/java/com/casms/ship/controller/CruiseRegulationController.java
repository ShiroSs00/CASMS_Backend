package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.dto.request.RegulationPrecheckRequest;
import com.casms.ship.dto.response.CruiseRegulationResponse;
import com.casms.ship.dto.response.RegulationAssignmentSnapshotResponse;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.service.CruiseRegulationService;
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

@Tag(name = "Cruise Regulation Management", description = "Quản lý và áp dụng quy định vận hành cho chuyến đi du thuyền (Cruise)")
@RestController
@RequestMapping("/cruises/{cruiseId}/regulations")
@RequiredArgsConstructor
public class CruiseRegulationController {

    private final CruiseRegulationService cruiseRegulationService;

    @Operation(summary = "Gán quy định cho chuyến đi", description = "Áp dụng một quy định vận hành cụ thể vào chuyến đi du thuyền")
    @PostMapping
    public ResponseEntity<ApiResponse<CruiseRegulationResponse>> assign(
            @PathVariable UUID cruiseId,
            @Valid @RequestBody CruiseRegulationRequest request) {

        request.setCruiseId(cruiseId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cruiseRegulationService.assign(request)));
    }

    @Operation(summary = "Kiểm tra xung đột trước khi gán cho chuyến đi", description = "Precheck xung đột quy định trước khi thực hiện gán cho chuyến đi")
    @PostMapping("/precheck")
    public ResponseEntity<ApiResponse<RegulationConflictResponse>> precheck(
            @PathVariable UUID cruiseId,
            @Valid @RequestBody RegulationPrecheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cruiseRegulationService.precheck(cruiseId, request.getRegulationId())));
    }

    @Operation(summary = "Danh sách quy định của chuyến đi", description = "Lấy danh sách tất cả các quy định đang áp dụng cho chuyến đi")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CruiseRegulationResponse>>> list(
            @PathVariable UUID cruiseId) {
        return ResponseEntity.ok(ApiResponse.success(cruiseRegulationService.list(cruiseId)));
    }

    @Operation(summary = "Snapshot quy định hiệu lực của chuyến đi", description = "Lấy snapshot các quy định hiệu lực trực tiếp và kế thừa của chuyến đi")
    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<RegulationAssignmentSnapshotResponse>> getEffectiveSnapshot(
            @PathVariable UUID cruiseId,
            @RequestParam(required = false) Instant at) {
        return ResponseEntity.ok(ApiResponse.success(
                cruiseRegulationService.getEffectiveSnapshot(cruiseId, at)));
    }

    @Operation(summary = "Gỡ quy định khỏi chuyến đi", description = "Gỡ bỏ áp dụng quy định khỏi chuyến đi")
    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID cruiseId,
            @PathVariable UUID regulationId) {
        cruiseRegulationService.remove(cruiseId, regulationId);
        return ResponseEntity.noContent().build();
    }
}
