package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.response.DeckServiceAssignmentResponse;
import com.casms.ship.service.DeckServiceAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/decks/{deckId}/services")
public class DeckServiceAssignmentController {
    private final DeckServiceAssignmentService service;

    @Operation(summary = "List catalogue services manually assigned to a deck")
    @PreAuthorize("hasAuthority('DECK_READ') and hasAuthority('SERVICE_READ')")
    @GetMapping
    public ApiResponse<List<DeckServiceAssignmentResponse>> list(@PathVariable UUID deckId) {
        return ApiResponse.success(service.list(deckId));
    }

    @Operation(summary = "Assign an existing service to a deck idempotently")
    @PreAuthorize("hasAuthority('DECK_READ') and hasAuthority('SERVICE_UPDATE')")
    @PutMapping("/{serviceId}")
    public ApiResponse<DeckServiceAssignmentResponse> assign(@PathVariable UUID deckId, @PathVariable UUID serviceId) {
        return ApiResponse.success(service.assign(deckId, serviceId));
    }

    @Operation(summary = "Remove a service assignment without deleting the catalogue service")
    @PreAuthorize("hasAuthority('DECK_READ') and hasAuthority('SERVICE_UPDATE')")
    @DeleteMapping("/{serviceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID deckId, @PathVariable UUID serviceId) {
        service.unassign(deckId, serviceId);
    }
}
