package com.casms.activity.controller;

import com.casms.activity.dto.request.ShoreExcursionCancellationRequest;
import com.casms.activity.dto.request.ShoreExcursionRequest;
import com.casms.activity.dto.response.ShoreExcursionResponse;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.service.ShoreExcursionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Shore Excursion Management", description = "Quản lý tour và hoạt động tham quan trên bờ cho hành khách")
@RestController
@RequestMapping("/shore-excursions")
@RequiredArgsConstructor
public class ShoreExcursionController {

    private final ShoreExcursionService shoreExcursionService;

    // CREATE
    @Operation(summary = "Tạo tour tham quan trên bờ", description = "Tạo mới thông tin tour tham quan trên bờ (Shore Excursion) ở trạng thái DRAFT")
    @PostMapping
    public ResponseEntity<ShoreExcursionResponse> create(
            @Valid @RequestBody ShoreExcursionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shoreExcursionService.create(request));
    }

    // READ ALL + FILTER
    @Operation(summary = "Danh sách tour tham quan trên bờ", description = "Lấy danh sách các tour tham quan trên bờ theo bộ lọc (điểm dừng cruiseStopId, nhà cung cấp supplierId, trạng thái)")
    @GetMapping
    public ResponseEntity<List<ShoreExcursionResponse>> list(
            @Parameter(description = "ID điểm dừng du thuyền") @RequestParam(required = false) UUID cruiseStopId,
            @Parameter(description = "ID nhà cung cấp tour") @RequestParam(required = false) UUID supplierId,
            @Parameter(description = "Trạng thái tour tham quan") @RequestParam(required = false) ShoreExcursionStatus status
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.list(
                        cruiseStopId, supplierId, status
                )
        );
    }

    // READ DETAIL
    @Operation(summary = "Chi tiết tour tham quan trên bờ", description = "Lấy thông tin chi tiết của một tour tham quan trên bờ theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ShoreExcursionResponse> detail(
            @Parameter(description = "ID tour tham quan trên bờ", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.detail(id)
        );
    }

    // UPDATE
    @Operation(summary = "Cập nhật tour tham quan trên bờ", description = "Cập nhật thông tin tour tham quan trên bờ khi ở trạng thái hợp lệ (DRAFT, OPEN)")
    @PutMapping("/{id}")
    public ResponseEntity<ShoreExcursionResponse> update(
            @Parameter(description = "ID tour tham quan trên bờ", required = true) @PathVariable UUID id,
            @Valid @RequestBody ShoreExcursionRequest request
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.update(id, request)
        );
    }

    // DELETE DRAFT
    @Operation(summary = "Xóa tour tham quan nháp", description = "Xóa tour tham quan trên bờ khi đang ở trạng thái DRAFT và chưa có phát sinh dữ liệu")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @Parameter(description = "ID tour tham quan nháp cần xóa", required = true) @PathVariable UUID id
    ) {
        shoreExcursionService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    // VALIDATE
    @Operation(summary = "Kiểm tra tính hợp lệ của tour", description = "Xác thực các điều kiện, thời gian và dữ liệu cấu hình của tour tham quan trên bờ")
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validate(
            @Parameter(description = "ID tour tham quan cần kiểm tra", required = true) @PathVariable UUID id
    ) {
        shoreExcursionService.validateExcursion(id);

        return ResponseEntity.ok(Map.of(
                "message", "Shore excursion is valid."
        ));
    }

    // CHECK AVAILABILITY
    @Operation(summary = "Kiểm tra tính khả dụng và chỗ trống", description = "Kiểm tra xem tour tham quan có đủ chỗ trống cho số lượng người tham gia yêu cầu không")
    @GetMapping("/{id}/availability")
    public ResponseEntity<ShoreExcursionResponse> checkAvailability(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id,
            @Parameter(description = "Số lượng khách tham gia cần kiểm tra", required = true) @RequestParam int participants
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.checkAvailability(id, participants)
        );
    }

    // REFRESH CAPACITY STATUS
    @Operation(summary = "Làm mới trạng thái sức chứa", description = "Cập nhật và tính toán lại sức chứa khả dụng của tour tham quan")
    @PatchMapping("/{id}/refresh-capacity")
    public ResponseEntity<ShoreExcursionResponse> refreshCapacity(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.refreshCapacity(id)
        );
    }

    // CONFIRM / OPEN FOR REGISTRATION
    @Operation(summary = "Xác nhận và mở đăng ký tour", description = "Chuyển trạng thái tour sang OPEN để hành khách có thể đăng ký tham gia")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ShoreExcursionResponse> confirm(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.confirm(id)
        );
    }

    // CLOSE REGISTRATION
    @Operation(summary = "Đóng đăng ký tour", description = "Đóng cổng đăng ký tour tham quan trên bờ (chuyển sang trạng thái CLOSED)")
    @PatchMapping("/{id}/close")
    public ResponseEntity<ShoreExcursionResponse> close(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.close(id)
        );
    }

    // START
    @Operation(summary = "Bắt đầu tour tham quan", description = "Bắt đầu khởi hành tour tham quan trên bờ (chuyển sang trạng thái IN_PROGRESS)")
    @PatchMapping("/{id}/start")
    public ResponseEntity<ShoreExcursionResponse> start(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.start(id)
        );
    }

    // COMPLETE
    @Operation(summary = "Hoàn thành tour tham quan", description = "Đánh dấu tour tham quan trên bờ đã kết thúc thành công (chuyển sang trạng thái COMPLETED)")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ShoreExcursionResponse> complete(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.complete(id)
        );
    }

    // CANCEL
    @Operation(summary = "Hủy tour tham quan trên bờ", description = "Hủy tour tham quan trên bờ kèm lý do hủy (chuyển sang trạng thái CANCELLED)")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ShoreExcursionResponse> cancel(
            @Parameter(description = "ID tour tham quan", required = true) @PathVariable UUID id,
            @Valid @RequestBody ShoreExcursionCancellationRequest request
    ) {
        return ResponseEntity.ok(
                shoreExcursionService.cancel(id, request.reason())
        );
    }
}