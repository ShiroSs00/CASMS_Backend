package com.casms.activity.controller;

import com.casms.activity.dto.request.AssignGuideRequest;
import com.casms.activity.dto.request.AssignPassengerRequest;
import com.casms.activity.dto.request.TourGroupCancellationRequest;
import com.casms.activity.dto.request.TourGroupRequest;
import com.casms.activity.dto.response.TourGroupResponse;
import com.casms.activity.enums.TourGroupStatus;
import com.casms.activity.service.TourGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tour-groups")
@RequiredArgsConstructor
public class TourGroupController {

    private final TourGroupService tourGroupService;

    // CREATE
    @PostMapping
    public ResponseEntity<TourGroupResponse> create(
            @Valid @RequestBody TourGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tourGroupService.create(request));
    }

    // READ LIST + FILTER
    @GetMapping
    public ResponseEntity<List<TourGroupResponse>> list(
            @RequestParam(required = false) UUID excursionId,
            @RequestParam(required = false) UUID guideId,
            @RequestParam(required = false) TourGroupStatus status
    ) {
        return ResponseEntity.ok(
                tourGroupService.list(excursionId, guideId, status)
        );
    }

    // READ DETAIL
    @GetMapping("/{id}")
    public ResponseEntity<TourGroupResponse> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.detail(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<TourGroupResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TourGroupRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.update(id, request)
        );
    }

    // ASSIGN GUIDE
    @PatchMapping("/{id}/guide")
    public ResponseEntity<TourGroupResponse> assignGuide(
            @PathVariable UUID id,
            @Valid @RequestBody AssignGuideRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.assignGuide(id, request.guideId())
        );
    }

    // ASSIGN PASSENGER
    @PostMapping("/{id}/passengers")
    public ResponseEntity<TourGroupResponse> assignPassenger(
            @PathVariable UUID id,
            @Valid @RequestBody AssignPassengerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tourGroupService.assignPassenger(
                        id, request.passengerId()
                ));
    }

    // REMOVE PASSENGER
    @DeleteMapping("/{id}/passengers/{passengerId}")
    public ResponseEntity<TourGroupResponse> removePassenger(
            @PathVariable UUID id,
            @PathVariable UUID passengerId
    ) {
        return ResponseEntity.ok(
                tourGroupService.removePassenger(id, passengerId)
        );
    }

    // VALIDATE GROUP
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validateGroup(
            @PathVariable UUID id
    ) {
        tourGroupService.validateGroup(id);

        return ResponseEntity.ok(Map.of(
                "message", "Tour group validation passed."
        ));
    }

    // CHECK CAPACITY
    @GetMapping("/{id}/capacity")
    public ResponseEntity<Map<String, Long>> checkCapacity(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(Map.of(
                "maximumCapacity",
                tourGroupService.detail(id).maximumCapacity().longValue(),
                "availableCapacity",
                tourGroupService.checkCapacity(id)
        ));
    }

    // CONFIRM / START ASSEMBLING
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<TourGroupResponse> confirm(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.confirm(id));
    }

    // CANCEL
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<TourGroupResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody TourGroupCancellationRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.cancel(id, request.reason())
        );
    }

    // START / DEPART
    @PatchMapping("/{id}/start")
    public ResponseEntity<TourGroupResponse> start(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.start(id));
    }

    // COMPLETE / RETURN
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TourGroupResponse> complete(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.complete(id));
    }
}