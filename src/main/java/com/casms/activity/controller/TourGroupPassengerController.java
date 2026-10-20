package com.casms.activity.controller;

import com.casms.activity.dto.request.TourGroupPassengerAssignRequest;
import com.casms.activity.dto.request.TourGroupPassengerMoveRequest;
import com.casms.activity.dto.response.TourGroupPassengerResponse;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.activity.service.TourGroupPassengerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Tour Group Passenger Management", description = "Quản lý phân bổ hành khách trong nhóm tour, điểm danh và điều chuyển nhóm")
@RestController
@RequestMapping("/tour-group-passengers")
public class TourGroupPassengerController {

    private final TourGroupPassengerService service;

    public TourGroupPassengerController(
            TourGroupPassengerService service
    ) {
        this.service = service;
    }

    // ASSIGN
    @Operation(summary = "Gán hành khách vào nhóm tour", description = "Đăng ký hành khách vào một nhóm tour cụ thể")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_ASSIGN')")
    @PostMapping
    public ResponseEntity<TourGroupPassengerResponse> assign(
            @Valid @RequestBody TourGroupPassengerAssignRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.assign(request));
    }

    // LIST WITH OPTIONAL FILTERS
    @Operation(summary = "Danh sách phân bổ hành khách", description = "Lấy danh sách gán hành khách theo bộ lọc (groupId, passengerId, trạng thái gán)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_READ')")
    @GetMapping
    public ResponseEntity<List<TourGroupPassengerResponse>> list(
            @Parameter(description = "ID nhóm tour") @RequestParam(required = false) UUID groupId,
            @Parameter(description = "ID hành khách") @RequestParam(required = false) UUID passengerId,
            @Parameter(description = "Trạng thái phân bổ hành khách") @RequestParam(required = false)
            TourGroupPassengerAssignmentStatus status
    ) {
        return ResponseEntity.ok(
                service.list(groupId, passengerId, status)
        );
    }

    // DETAIL
    @Operation(summary = "Chi tiết phân bổ hành khách", description = "Xem chi tiết thông tin một lượt phân bổ hành khách theo ID")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<TourGroupPassengerResponse> detail(
            @Parameter(description = "ID phân bổ hành khách", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.detail(id));
    }

    // MOVE TO ANOTHER GROUP
    @Operation(summary = "Chuyển hành khách sang nhóm khác", description = "Điều chuyển hành khách từ nhóm hiện tại sang nhóm tour khác cùng tour tham quan")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_MOVE')")
    @PatchMapping("/{id}/move")
    public ResponseEntity<TourGroupPassengerResponse> move(
            @Parameter(description = "ID phân bổ hiện tại của hành khách", required = true) @PathVariable UUID id,
            @Valid @RequestBody TourGroupPassengerMoveRequest request
    ) {
        return ResponseEntity.ok(
                service.move(id, request.targetTourGroupId())
        );
    }

    // REMOVE BY SOFT CANCELLATION
    @Operation(summary = "Hủy phân bổ hành khách", description = "Hủy gán hành khách khỏi nhóm tour (hủy mềm)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_REMOVE')")
    @PatchMapping("/{id}/remove")
    public ResponseEntity<TourGroupPassengerResponse> remove(
            @Parameter(description = "ID phân bổ hành khách cần hủy", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.remove(id));
    }

    // RECORD CHECK-IN BEFORE DEPARTURE
    @Operation(summary = "Điểm danh hành khách lúc khởi hành", description = "Ghi nhận hành khách đã có mặt và lên xe khởi hành đi tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_ATTENDANCE')")
    @PatchMapping("/{id}/departure")
    public ResponseEntity<TourGroupPassengerResponse> recordDeparture(
            @Parameter(description = "ID phân bổ hành khách", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.recordDeparture(id));
    }

    // RECORD RETURN / COMPLETION
    @Operation(summary = "Điểm danh hành khách lúc quay về", description = "Ghi nhận hành khách đã hoàn thành tour và quay trở lại tàu an toàn")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_ATTENDANCE')")
    @PatchMapping("/{id}/return")
    public ResponseEntity<TourGroupPassengerResponse> recordReturn(
            @Parameter(description = "ID phân bổ hành khách", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.recordReturn(id));
    }

    // VALIDATE ASSIGNMENT
    @Operation(summary = "Kiểm tra tính hợp lệ phân bổ", description = "Kiểm tra xem việc phân bổ hành khách vào nhóm tour có hợp lệ và thỏa mãn điều kiện không")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_VALIDATE')")
    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validateAssignment(
            @Parameter(description = "ID nhóm tour", required = true) @RequestParam UUID groupId,
            @Parameter(description = "ID hành khách", required = true) @RequestParam UUID passengerId
    ) {
        service.validateAssignment(groupId, passengerId);

        return ResponseEntity.ok(Map.of(
                "message", "Passenger assignment is valid"
        ));
    }

    // CHECK AVAILABLE GROUP CAPACITY
    @Operation(summary = "Kiểm tra chỗ trống của nhóm tour", description = "Tra cứu số chỗ khả dụng còn lại của một nhóm tour cụ thể")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_READ')")
    @GetMapping("/groups/{groupId}/available-capacity")
    public ResponseEntity<Map<String, Long>> checkGroupCapacity(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID groupId
    ) {
        return ResponseEntity.ok(Map.of(
                "availableCapacity",
                service.checkGroupCapacity(groupId)
        ));
    }

    // CHECK PASSENGER ELIGIBILITY
    @Operation(summary = "Kiểm tra điều kiện tham gia của hành khách", description = "Kiểm tra xem hành khách có đủ điều kiện tham gia tour tham quan không")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_READ')")
    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Boolean>> checkPassengerEligibility(
            @Parameter(description = "ID hành khách", required = true) @RequestParam UUID passengerId,
            @Parameter(description = "ID tour tham quan", required = true) @RequestParam UUID excursionId
    ) {
        return ResponseEntity.ok(Map.of(
                "eligible",
                service.checkPassengerEligibility(
                        passengerId,
                        excursionId
                )
        ));
    }

    // LIST ELIGIBLE PASSENGERS
    @Operation(summary = "Danh sách hành khách đủ điều kiện gán vào nhóm", description = "Lấy danh sách hành khách đã có đăng ký xác nhận (CONFIRMED) cho tour và chưa được gán vào nhóm nào")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_READ')")
    @GetMapping("/groups/{groupId}/eligible-passengers")
    public ResponseEntity<List<com.casms.activity.dto.response.EligiblePassengerResponse>> listEligiblePassengers(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID groupId
    ) {
        return ResponseEntity.ok(service.listEligiblePassengers(groupId));
    }
}