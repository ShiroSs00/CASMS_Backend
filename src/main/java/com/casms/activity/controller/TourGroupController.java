package com.casms.activity.controller;

import com.casms.activity.dto.request.AssignGuideRequest;
import com.casms.activity.dto.request.AssignPassengerRequest;
import com.casms.activity.dto.request.TourGroupCancellationRequest;
import com.casms.activity.dto.request.TourGroupRequest;
import com.casms.activity.dto.response.TourGroupResponse;
import com.casms.activity.enums.TourGroupStatus;
import com.casms.activity.service.TourGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Tour Group Management", description = "Quản lý đoàn/nhóm tham quan tour trên bờ, phân công hướng dẫn viên")
@RestController
@RequestMapping("/tour-groups")
@RequiredArgsConstructor
public class TourGroupController {

    private final TourGroupService tourGroupService;

    // CREATE
    @Operation(summary = "Tạo nhóm tour mới", description = "Khởi tạo một nhóm/đoàn tham quan mới cho tour trên bờ (trạng thái DRAFT/OPEN)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_CREATE')")
    @PostMapping
    public ResponseEntity<TourGroupResponse> create(
            @Valid @RequestBody TourGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tourGroupService.create(request));
    }

    // READ LIST + FILTER
    @Operation(summary = "Danh sách nhóm tour", description = "Lấy danh sách các nhóm tour theo bộ lọc (mã tour excursionId, hướng dẫn viên guideId, trạng thái)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_READ')")
    @GetMapping
    public ResponseEntity<List<TourGroupResponse>> list(
            @Parameter(description = "ID tour tham quan") @RequestParam(required = false) UUID excursionId,
            @Parameter(description = "ID hướng dẫn viên") @RequestParam(required = false) UUID guideId,
            @Parameter(description = "Trạng thái nhóm tour") @RequestParam(required = false) TourGroupStatus status
    ) {
        return ResponseEntity.ok(
                tourGroupService.list(excursionId, guideId, status)
        );
    }

    // READ DETAIL
    @Operation(summary = "Chi tiết nhóm tour", description = "Xem thông tin chi tiết một nhóm tour theo ID")
    @PreAuthorize("hasAuthority('TOUR_GROUP_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<TourGroupResponse> detail(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.detail(id));
    }

    // UPDATE
    @Operation(summary = "Cập nhật nhóm tour", description = "Cập nhật thông tin nhóm tour (tên nhóm, số lượng tối đa, ghi chú)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<TourGroupResponse> update(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id,
            @Valid @RequestBody TourGroupRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.update(id, request)
        );
    }

    // ASSIGN GUIDE
    @Operation(summary = "Phân công hướng dẫn viên", description = "Gán hoặc thay đổi hướng dẫn viên phụ trách cho nhóm tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_GUIDE_ASSIGN')")
    @PatchMapping("/{id}/guide")
    public ResponseEntity<TourGroupResponse> assignGuide(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignGuideRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.assignGuide(id, request.guideId())
        );
    }

    // ASSIGN PASSENGER
    @Operation(summary = "Thêm hành khách vào nhóm tour", description = "Gán một hành khách vào nhóm tour (kiểm tra sức chứa khả dụng của nhóm)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_ASSIGN')")
    @PostMapping("/{id}/passengers")
    public ResponseEntity<TourGroupResponse> assignPassenger(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignPassengerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tourGroupService.assignPassenger(
                        id, request.passengerId()
                ));
    }

    // REMOVE PASSENGER
    @Operation(summary = "Xóa hành khách khỏi nhóm tour", description = "Gỡ bỏ hành khách ra khỏi danh sách nhóm tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_PASSENGER_REMOVE')")
    @DeleteMapping("/{id}/passengers/{passengerId}")
    public ResponseEntity<TourGroupResponse> removePassenger(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id,
            @Parameter(description = "ID hành khách cần gỡ khỏi nhóm", required = true) @PathVariable UUID passengerId
    ) {
        return ResponseEntity.ok(
                tourGroupService.removePassenger(id, passengerId)
        );
    }

    // VALIDATE GROUP
    @Operation(summary = "Kiểm tra tính hợp lệ nhóm tour", description = "Xác thực các ràng buộc về thành viên, hướng dẫn viên và sức chứa của nhóm tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_VALIDATE')")
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validateGroup(
            @Parameter(description = "ID nhóm tour cần kiểm tra", required = true) @PathVariable UUID id
    ) {
        tourGroupService.validateGroup(id);

        return ResponseEntity.ok(Map.of(
                "message", "Tour group validation passed."
        ));
    }

    // CHECK CAPACITY
    @Operation(summary = "Kiểm tra sức chứa của nhóm tour", description = "Lấy thông tin sức chứa tối đa và số chỗ còn trống của nhóm tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_READ')")
    @GetMapping("/{id}/capacity")
    public ResponseEntity<Map<String, Long>> checkCapacity(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(Map.of(
                "maximumCapacity",
                tourGroupService.detail(id).maximumCapacity().longValue(),
                "availableCapacity",
                tourGroupService.checkCapacity(id)
        ));
    }

    // CONFIRM / START ASSEMBLING
    @Operation(summary = "Xác nhận nhóm tour", description = "Xác nhận chốt danh sách và chuẩn bị khởi hành cho nhóm tour")
    @PreAuthorize("hasAuthority('TOUR_GROUP_STATUS_CHANGE')")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<TourGroupResponse> confirm(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.confirm(id));
    }

    // CANCEL
    @Operation(summary = "Hủy nhóm tour", description = "Hủy nhóm tour kèm theo lý do hủy (chuyển sang trạng thái CANCELLED)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_STATUS_CHANGE')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<TourGroupResponse> cancel(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id,
            @Valid @RequestBody TourGroupCancellationRequest request
    ) {
        return ResponseEntity.ok(
                tourGroupService.cancel(id, request.reason())
        );
    }

    // START / DEPART
    @Operation(summary = "Khởi hành nhóm tour", description = "Bắt đầu lịch trình tham quan của nhóm tour (chuyển sang trạng thái IN_PROGRESS)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_STATUS_CHANGE')")
    @PatchMapping("/{id}/start")
    public ResponseEntity<TourGroupResponse> start(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.start(id));
    }

    // COMPLETE / RETURN
    @Operation(summary = "Hoàn thành nhóm tour", description = "Đánh dấu nhóm tour đã kết thúc và trở về an toàn (chuyển sang trạng thái COMPLETED)")
    @PreAuthorize("hasAuthority('TOUR_GROUP_STATUS_CHANGE')")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TourGroupResponse> complete(
            @Parameter(description = "ID nhóm tour", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(tourGroupService.complete(id));
    }
}