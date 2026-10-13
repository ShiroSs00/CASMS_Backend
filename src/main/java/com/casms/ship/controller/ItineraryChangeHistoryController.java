package com.casms.ship.controller;

import com.casms.ship.dto.request.ItineraryChangeRequest;
import com.casms.ship.dto.response.ItineraryChangeHistoryResponse;
import com.casms.ship.enums.ItineraryChangeType;
import com.casms.ship.service.ItineraryChangeHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/itinerary-change-history")
public class ItineraryChangeHistoryController {

    private final ItineraryChangeHistoryService service;

    public ItineraryChangeHistoryController(
            ItineraryChangeHistoryService service
    ) {
        this.service = service;
    }

    // APPEND / RECORD CHANGE

    @PostMapping
    public ResponseEntity<ItineraryChangeHistoryResponse> append(
            @Valid @RequestBody ItineraryChangeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordItineraryChange(request));
    }

    // LIST / FILTER

    @GetMapping
    public ResponseEntity<List<ItineraryChangeHistoryResponse>> list(
            @RequestParam(required = false) UUID itineraryId,
            @RequestParam(required = false) UUID cruiseStopId,
            @RequestParam(required = false) ItineraryChangeType changeType,
            @RequestParam(required = false) UUID changedByUserId
    ) {
        return ResponseEntity.ok(
                service.list(
                        itineraryId,
                        cruiseStopId,
                        changeType,
                        changedByUserId
                )
        );
    }

    // DETAIL

    @GetMapping("/{historyId}")
    public ResponseEntity<ItineraryChangeHistoryResponse> detail(
            @PathVariable UUID historyId
    ) {
        return ResponseEntity.ok(service.detail(historyId));
    }

    // GET ALL CHANGES OF AN ITINERARY

    @GetMapping("/itineraries/{itineraryId}")
    public ResponseEntity<List<ItineraryChangeHistoryResponse>>
    getChangeHistory(
            @PathVariable UUID itineraryId
    ) {
        return ResponseEntity.ok(
                service.getChangeHistory(itineraryId)
        );
    }
}