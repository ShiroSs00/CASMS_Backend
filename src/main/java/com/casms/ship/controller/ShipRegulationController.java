package com.casms.ship.controller;

import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.dto.response.ShipRegulationResponse;
import com.casms.ship.service.ShipRegulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ships/{shipId}/regulations")
@RequiredArgsConstructor
public class ShipRegulationController {

    private final ShipRegulationService shipRegulationService;

    @PostMapping
    public ResponseEntity<ShipRegulationResponse> assign(
            @PathVariable UUID shipId,
            @Valid @RequestBody ShipRegulationRequest request) {

        request.setShipId(shipId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipRegulationService.assign(request));
    }

    @GetMapping
    public ResponseEntity<List<ShipRegulationResponse>> list(
            @PathVariable UUID shipId) {
        return ResponseEntity.ok(shipRegulationService.list(shipId));
    }

    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID shipId,
            @PathVariable UUID regulationId) {
        shipRegulationService.remove(shipId, regulationId);
        return ResponseEntity.noContent().build();
    }
}
