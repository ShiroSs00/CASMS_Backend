package com.casms.ship.controller;

import com.casms.ship.dto.request.OperationalRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.service.OperationalRegulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/operational-regulations")
@RequiredArgsConstructor
public class OperationalRegulationController {

    private final OperationalRegulationService regulationService;

    @PostMapping
    public ResponseEntity<OperationalRegulationResponse> create(
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(regulationService.createRegulation(request));
    }

    @GetMapping
    public ResponseEntity<List<OperationalRegulationResponse>> list() {
        return ResponseEntity.ok(regulationService.listRegulations());
    }

    @GetMapping("/{regulationId}")
    public ResponseEntity<OperationalRegulationResponse> detail(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.getRegulationDetail(regulationId));
    }

    @PutMapping("/{regulationId}")
    public ResponseEntity<OperationalRegulationResponse> update(
            @PathVariable UUID regulationId,
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.ok(
                regulationService.updateRegulation(regulationId, request));
    }

    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID regulationId) {
        regulationService.deleteUnusedRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{regulationId}/activate")
    public ResponseEntity<OperationalRegulationResponse> activate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.activateRegulation(regulationId));
    }

    @PatchMapping("/{regulationId}/deactivate")
    public ResponseEntity<OperationalRegulationResponse> deactivate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.deactivateRegulation(regulationId));
    }

    @GetMapping("/effective")
    public ResponseEntity<List<OperationalRegulationResponse>> getEffective() {
        return ResponseEntity.ok(
                regulationService.getEffectiveRegulations());
    }

    @PostMapping("/{regulationId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID regulationId) {
        regulationService.validateRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{regulationId}/conflicts")
    public ResponseEntity<List<OperationalRegulationResponse>> validateConflict(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.validateConflict(regulationId));
    }
}
