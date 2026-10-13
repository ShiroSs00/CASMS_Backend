package com.casms.ship.controller;

import com.casms.ship.dto.request.OperationalIssueAssignRequest;
import com.casms.ship.dto.request.OperationalIssueRequest;
import com.casms.ship.dto.request.OperationalIssueResolutionRequest;
import com.casms.ship.dto.request.OperationalIssueUpdateRequest;
import com.casms.ship.dto.response.OperationalIssueResponse;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.service.OperationalIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/operational-issues")
@RequiredArgsConstructor
public class OperationalIssueController {

    private final OperationalIssueService issueService;

    @PostMapping
    public ResponseEntity<OperationalIssueResponse> report(
            @Valid @RequestBody OperationalIssueRequest request,
            @RequestParam UUID reporterId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issueService.report(request, reporterId));
    }

    @GetMapping
    public ResponseEntity<List<OperationalIssueResponse>> list(
            @RequestParam(required = false) OperationalIssueStatus status,
            @RequestParam(required = false) UUID shipId,
            @RequestParam(required = false) UUID cruiseId) {
        return ResponseEntity.ok(
                issueService.list(status, shipId, cruiseId));
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<OperationalIssueResponse> detail(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.detail(issueId));
    }

    @PutMapping("/{issueId}")
    public ResponseEntity<OperationalIssueResponse> update(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueUpdateRequest request) {
        return ResponseEntity.ok(
                issueService.updateOpen(issueId, request));
    }

    @PatchMapping("/{issueId}/assign")
    public ResponseEntity<OperationalIssueResponse> assignHandler(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueAssignRequest request) {
        return ResponseEntity.ok(
                issueService.assignHandler(issueId, request));
    }

    @PatchMapping("/{issueId}/start")
    public ResponseEntity<OperationalIssueResponse> startHandling(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(
                issueService.startHandling(issueId));
    }

    @PostMapping("/{issueId}/resolution")
    public ResponseEntity<OperationalIssueResponse> recordResolution(
            @PathVariable UUID issueId,
            @RequestParam UUID resolverId,
            @Valid @RequestBody OperationalIssueResolutionRequest request) {
        return ResponseEntity.ok(
                issueService.recordResolution(issueId, resolverId, request));
    }

    @PatchMapping("/{issueId}/verify")
    public ResponseEntity<OperationalIssueResponse> verifyResolution(
            @PathVariable UUID issueId,
            @RequestParam UUID verifierId) {
        return ResponseEntity.ok(
                issueService.verifyResolution(issueId, verifierId));
    }

    @PatchMapping("/{issueId}/close")
    public ResponseEntity<OperationalIssueResponse> close(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.close(issueId));
    }

    @PatchMapping("/{issueId}/reopen")
    public ResponseEntity<OperationalIssueResponse> reopen(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.reopen(issueId));
    }

    @PostMapping("/{issueId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID issueId) {
        issueService.validateIssue(issueId);
        return ResponseEntity.noContent().build();
    }
}
