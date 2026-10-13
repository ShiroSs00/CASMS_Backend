package com.casms.ship.controller;

import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.dto.response.CruiseRegulationResponse;
import com.casms.ship.service.CruiseRegulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<CruiseRegulationResponse> assign(
            @PathVariable UUID cruiseId,
            @Valid @RequestBody CruiseRegulationRequest request) {

        request.setCruiseId(cruiseId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cruiseRegulationService.assign(request));
    }

    @Operation(summary = "Danh sách quy định của chuyến đi", description = "Lấy danh sách tất cả các quy định đang áp dụng cho chuyến đi")
    @GetMapping
    public ResponseEntity<List<CruiseRegulationResponse>> list(
            @PathVariable UUID cruiseId) {
        return ResponseEntity.ok(cruiseRegulationService.list(cruiseId));
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
