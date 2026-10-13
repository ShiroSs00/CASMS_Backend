package com.casms.activity.controller;

import com.casms.activity.dto.request.TourGroupPassengerAssignRequest;
import com.casms.activity.dto.request.TourGroupPassengerMoveRequest;
import com.casms.activity.dto.response.TourGroupPassengerResponse;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.activity.service.TourGroupPassengerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tour-group-passengers")
public class TourGroupPassengerController {

    private final TourGroupPassengerService service;

    public TourGroupPassengerController(
            TourGroupPassengerService service
    ) {
        this.service = service;
    }

    // ASSIGN
    @PostMapping
    public ResponseEntity<TourGroupPassengerResponse> assign(
            @Valid @RequestBody TourGroupPassengerAssignRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.assign(request));
    }

    // LIST WITH OPTIONAL FILTERS
    @GetMapping
    public ResponseEntity<List<TourGroupPassengerResponse>> list(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID passengerId,
            @RequestParam(required = false)
            TourGroupPassengerAssignmentStatus status
    ) {
        return ResponseEntity.ok(
                service.list(groupId, passengerId, status)
        );
    }

    // DETAIL
    @GetMapping("/{id}")
    public ResponseEntity<TourGroupPassengerResponse> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.detail(id));
    }

    // MOVE TO ANOTHER GROUP
    @PatchMapping("/{id}/move")
    public ResponseEntity<TourGroupPassengerResponse> move(
            @PathVariable UUID id,
            @Valid @RequestBody TourGroupPassengerMoveRequest request
    ) {
        return ResponseEntity.ok(
                service.move(id, request.targetTourGroupId())
        );
    }

    // REMOVE BY SOFT CANCELLATION
    @PatchMapping("/{id}/remove")
    public ResponseEntity<TourGroupPassengerResponse> remove(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.remove(id));
    }

    // RECORD CHECK-IN BEFORE DEPARTURE
    @PatchMapping("/{id}/departure")
    public ResponseEntity<TourGroupPassengerResponse> recordDeparture(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.recordDeparture(id));
    }

    // RECORD RETURN / COMPLETION
    @PatchMapping("/{id}/return")
    public ResponseEntity<TourGroupPassengerResponse> recordReturn(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.recordReturn(id));
    }

    // VALIDATE ASSIGNMENT
    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validateAssignment(
            @RequestParam UUID groupId,
            @RequestParam UUID passengerId
    ) {
        service.validateAssignment(groupId, passengerId);

        return ResponseEntity.ok(Map.of(
                "message", "Passenger assignment is valid"
        ));
    }

    // CHECK AVAILABLE GROUP CAPACITY
    @GetMapping("/groups/{groupId}/available-capacity")
    public ResponseEntity<Map<String, Long>> checkGroupCapacity(
            @PathVariable UUID groupId
    ) {
        return ResponseEntity.ok(Map.of(
                "availableCapacity",
                service.checkGroupCapacity(groupId)
        ));
    }

    // CHECK PASSENGER ELIGIBILITY
    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Boolean>> checkPassengerEligibility(
            @RequestParam UUID passengerId,
            @RequestParam UUID excursionId
    ) {
        return ResponseEntity.ok(Map.of(
                "eligible",
                service.checkPassengerEligibility(
                        passengerId,
                        excursionId
                )
        ));
    }
}