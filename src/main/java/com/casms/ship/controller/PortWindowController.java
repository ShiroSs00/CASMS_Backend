package com.casms.ship.controller;

import com.casms.audit.AuditLog;
import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.service.PortWindowService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ports/{portId}/call-windows")
@Tag(
        name = "Port Call Windows",
        description =
                "OPS source-verified Port-local arrival/departure hours and closures. Read-only CRM"
                    + " references; no berth reservation or template window pinning.")
public class PortWindowController {
    private final PortWindowService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PORT_CALL_WINDOW_READ','PORT_READ','TRIP_READ')")
    public ApiResponse<Directory> read(@PathVariable UUID portId) {
        return ApiResponse.success(service.read(portId));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_READ')")
    public ApiResponse<Page<AuditLog>> history(
            @PathVariable UUID portId,
            @PathVariable UUID id,
            Pageable pageable) {
        return ApiResponse.success(service.history(portId, id, false, pageable));
    }

    @GetMapping("/exceptions/{id}/history")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_READ')")
    public ApiResponse<Page<AuditLog>>
            exceptionHistory(
                    @PathVariable UUID portId,
                    @PathVariable UUID id,
                    Pageable pageable) {
        return ApiResponse.success(service.history(portId, id, true, pageable));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_CREATE')")
    public ApiResponse<Window> create(
            @PathVariable UUID portId, @Valid @RequestBody WindowWrite body) {
        return ApiResponse.success(service.write(portId, null, body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_UPDATE')")
    public ApiResponse<Window> update(
            @PathVariable UUID portId,
            @PathVariable UUID id,
            @Valid @RequestBody WindowWrite body) {
        return ApiResponse.success(service.write(portId, id, body));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_VERIFY')")
    public ApiResponse<Window> verify(
            @PathVariable UUID portId, @PathVariable UUID id, @Valid @RequestBody Revision body) {
        return ApiResponse.success(service.verify(portId, id, body));
    }

    @PatchMapping("/{id}/lifecycle")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_UPDATE')")
    public ApiResponse<Window> lifecycle(
            @PathVariable UUID portId, @PathVariable UUID id, @Valid @RequestBody Lifecycle body) {
        return ApiResponse.success(service.lifecycle(portId, id, body));
    }

    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PostMapping("/exceptions")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_CREATE')")
    public ApiResponse<Closure> createClosure(
            @PathVariable UUID portId, @Valid @RequestBody ClosureWrite body) {
        return ApiResponse.success(service.writeClosure(portId, null, body));
    }

    @PutMapping("/exceptions/{id}")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_UPDATE')")
    public ApiResponse<Closure> updateClosure(
            @PathVariable UUID portId,
            @PathVariable UUID id,
            @Valid @RequestBody ClosureWrite body) {
        return ApiResponse.success(service.writeClosure(portId, id, body));
    }

    @PostMapping("/exceptions/{id}/verify")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_VERIFY')")
    public ApiResponse<Closure> verifyClosure(
            @PathVariable UUID portId, @PathVariable UUID id, @Valid @RequestBody Revision body) {
        return ApiResponse.success(service.verifyClosure(portId, id, body));
    }

    @PatchMapping("/exceptions/{id}/lifecycle")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('PORT_CALL_WINDOW_UPDATE')")
    public ApiResponse<Closure> lifecycleClosure(
            @PathVariable UUID portId, @PathVariable UUID id, @Valid @RequestBody Lifecycle body) {
        return ApiResponse.success(service.lifecycleClosure(portId, id, body));
    }
}
