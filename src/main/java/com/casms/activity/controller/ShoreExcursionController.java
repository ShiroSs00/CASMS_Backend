package com.casms.activity.controller;

import com.casms.activity.dto.request.ShoreExcursionCancellationRequest;
import com.casms.activity.dto.request.ShoreExcursionRequest;
import com.casms.activity.dto.response.ShoreExcursionResponse;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.service.ShoreExcursionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shore-excursions")
@RequiredArgsConstructor
public class ShoreExcursionController {

    private final ShoreExcursionService shoreExcursionService;

    // CREATE
    @PostMapping
    public ResponseEntity<ShoreExcursionResponse> create(
            @Valid @RequestBody ShoreExcursionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shoreExcursionService.create(request));
    }

    // READ ALL + FILTER
    @GetMapping
    public ResponseEntity<List<ShoreExcursionResponse>> list(
            @RequestParam(required = false) UUID cruiseStopId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) ShoreExcursionStatus status
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.list(
                        cruiseStopId, supplierId, status
                )
        );
    }

    // READ DETAIL
    @GetMapping("/{id}")
    public ResponseEntity<ShoreExcursionResponse> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.detail(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ShoreExcursionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ShoreExcursionRequest request
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.update(id, request)
        );
    }

    // DELETE DRAFT
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable UUID id
    ) {
        shoreExcursionService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    // VALIDATE
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validate(
            @PathVariable UUID id
    ) {
        shoreExcursionService.validateExcursion(id);

        return ResponseEntity.ok(Map.of(
                "message", "Shore excursion is valid."
        ));
    }

    // CHECK AVAILABILITY
    @GetMapping("/{id}/availability")
    public ResponseEntity<ShoreExcursionResponse> checkAvailability(
            @PathVariable UUID id,
            @RequestParam int participants
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.checkAvailability(id, participants)
        );
    }

    // REFRESH CAPACITY STATUS
    @PatchMapping("/{id}/refresh-capacity")
    public ResponseEntity<ShoreExcursionResponse> refreshCapacity(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.refreshCapacity(id)
        );
    }

    // CONFIRM / OPEN FOR REGISTRATION
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ShoreExcursionResponse> confirm(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.confirm(id)
        );
    }

    // CLOSE REGISTRATION
    @PatchMapping("/{id}/close")
    public ResponseEntity<ShoreExcursionResponse> close(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.close(id)
        );
    }

    // START
    @PatchMapping("/{id}/start")
    public ResponseEntity<ShoreExcursionResponse> start(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.start(id)
        );
    }

    // COMPLETE
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ShoreExcursionResponse> complete(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.complete(id)
        );
    }

    // CANCEL
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ShoreExcursionResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody ShoreExcursionCancellationRequest request
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.cancel(id, request.reason())
        );
    }
}