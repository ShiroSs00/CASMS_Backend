package com.casms.ship.controller;

import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.dto.response.ShipRegulationResponse;
import com.casms.ship.service.ShipRegulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ShipRegulationResponse> assign(
            @PathVariable UUID shipId,
            @Valid @RequestBody ShipRegulationRequest request) {

        request.setShipId(shipId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipRegulationService.assign(request));
    }

    @Operation(summary = "Danh sách quy định của tàu", description = "Lấy danh sách tất cả các quy định đang áp dụng cho tàu")
    @GetMapping
    public ResponseEntity<List<ShipRegulationResponse>> list(
            @PathVariable UUID shipId) {
        return ResponseEntity.ok(shipRegulationService.list(shipId));
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
