package com.casms.ship.controller;

import com.casms.ship.dto.request.ItineraryChangeRequest;
import com.casms.ship.dto.response.ItineraryChangeHistoryResponse;
import com.casms.ship.enums.ItineraryChangeType;
import com.casms.ship.service.ItineraryChangeHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Itinerary Change History Management", description = "Quản lý và tra cứu lịch sử thay đổi lịch trình chuyến đi")
@RestController
@RequestMapping("/itinerary-change-history")
public class ItineraryChangeHistoryController {

    private final ItineraryChangeHistoryService service;

    public ItineraryChangeHistoryController(
            ItineraryChangeHistoryService service
    ) {
        this.service = service;
    }

    // APPEND / RECORD CHANGE
    @Operation(summary = "Ghi nhận thay đổi lịch trình", description = "Thêm bản ghi mới vào lịch sử điều chỉnh lịch trình chuyến đi")
    @PreAuthorize("hasAuthority('ITINERARY_CHANGE_RECORD')")
    @PostMapping
    public ResponseEntity<ItineraryChangeHistoryResponse> append(
            @Valid @RequestBody ItineraryChangeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordItineraryChange(request));
    }

    // LIST / FILTER
    @Operation(summary = "Danh sách lịch sử thay đổi", description = "Tra cứu danh sách lịch sử thay đổi lịch trình theo bộ lọc (itineraryId, cruiseStopId, loại thay đổi, người thay đổi)")
    @PreAuthorize("hasAuthority('ITINERARY_CHANGE_READ')")
    @GetMapping
    public ResponseEntity<List<ItineraryChangeHistoryResponse>> list(
            @Parameter(description = "ID lịch trình") @RequestParam(required = false) UUID itineraryId,
            @Parameter(description = "ID điểm dừng du thuyền") @RequestParam(required = false) UUID cruiseStopId,
            @Parameter(description = "Loại thay đổi lịch trình") @RequestParam(required = false) ItineraryChangeType changeType,
            @Parameter(description = "ID người thực hiện thay đổi") @RequestParam(required = false) UUID changedByUserId
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
    @Operation(summary = "Chi tiết bản ghi thay đổi lịch trình", description = "Xem chi tiết một bản ghi thay đổi lịch trình theo ID lịch sử")
    @PreAuthorize("hasAuthority('ITINERARY_CHANGE_READ')")
    @GetMapping("/{historyId}")
    public ResponseEntity<ItineraryChangeHistoryResponse> detail(
            @Parameter(description = "ID của bản ghi lịch sử thay đổi", required = true) @PathVariable UUID historyId
    ) {
        return ResponseEntity.ok(service.detail(historyId));
    }

    // GET ALL CHANGES OF AN ITINERARY
    @Operation(summary = "Lịch sử thay đổi theo lịch trình", description = "Lấy toàn bộ danh sách các lần điều chỉnh, sửa đổi của một lịch trình cụ thể")
    @PreAuthorize("hasAuthority('ITINERARY_CHANGE_READ')")
    @GetMapping("/itineraries/{itineraryId}")
    public ResponseEntity<List<ItineraryChangeHistoryResponse>>
    getChangeHistory(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId
    ) {
        return ResponseEntity.ok(
                service.getChangeHistory(itineraryId)
        );
    }
}