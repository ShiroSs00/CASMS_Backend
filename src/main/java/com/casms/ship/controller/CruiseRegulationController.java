package com.casms.ship.controller;

import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.dto.response.CruiseRegulationResponse;
import com.casms.ship.service.CruiseRegulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cruises/{cruiseId}/regulations")
@RequiredArgsConstructor
public class CruiseRegulationController {

    private final CruiseRegulationService cruiseRegulationService;

    @PostMapping
    public ResponseEntity<CruiseRegulationResponse> assign(
            @PathVariable UUID cruiseId,
            @Valid @RequestBody CruiseRegulationRequest request) {

        request.setCruiseId(cruiseId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cruiseRegulationService.assign(request));
    }

    @GetMapping
    public ResponseEntity<List<CruiseRegulationResponse>> list(
            @PathVariable UUID cruiseId) {
        return ResponseEntity.ok(cruiseRegulationService.list(cruiseId));
    }

    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID cruiseId,
            @PathVariable UUID regulationId) {
        cruiseRegulationService.remove(cruiseId, regulationId);
        return ResponseEntity.noContent().build();
    }
}
