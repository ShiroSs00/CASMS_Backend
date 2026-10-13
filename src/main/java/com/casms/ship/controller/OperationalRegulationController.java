package com.casms.ship.controller;

import com.casms.ship.dto.request.OperationalRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.service.OperationalRegulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Operational Regulation Management", description = "Quản lý danh mục quy định vận hành và an toàn hàng hải")
@RestController
@RequestMapping("/operational-regulations")
@RequiredArgsConstructor
public class OperationalRegulationController {

    private final OperationalRegulationService regulationService;

    @Operation(summary = "Tạo quy định mới", description = "Khởi tạo một quy định vận hành mới trong hệ thống")
    @PostMapping
    public ResponseEntity<OperationalRegulationResponse> create(
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(regulationService.createRegulation(request));
    }

    @Operation(summary = "Danh sách quy định", description = "Lấy toàn bộ danh sách quy định vận hành")
    @GetMapping
    public ResponseEntity<List<OperationalRegulationResponse>> list() {
        return ResponseEntity.ok(regulationService.listRegulations());
    }

    @Operation(summary = "Chi tiết quy định", description = "Lấy thông tin chi tiết của một quy định vận hành theo ID")
    @GetMapping("/{regulationId}")
    public ResponseEntity<OperationalRegulationResponse> detail(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.getRegulationDetail(regulationId));
    }

    @Operation(summary = "Cập nhật quy định", description = "Chỉnh sửa nội dung, tham số của quy định vận hành")
    @PutMapping("/{regulationId}")
    public ResponseEntity<OperationalRegulationResponse> update(
            @PathVariable UUID regulationId,
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.ok(
                regulationService.updateRegulation(regulationId, request));
    }

    @Operation(summary = "Xóa quy định", description = "Xóa quy định vận hành chưa được sử dụng")
    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID regulationId) {
        regulationService.deleteUnusedRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kích hoạt quy định", description = "Chuyển trạng thái quy định sang hiệu lực (ACTIVE)")
    @PatchMapping("/{regulationId}/activate")
    public ResponseEntity<OperationalRegulationResponse> activate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.activateRegulation(regulationId));
    }

    @Operation(summary = "Hủy kích hoạt quy định", description = "Chuyển trạng thái quy định sang ngưng hiệu lực (INACTIVE)")
    @PatchMapping("/{regulationId}/deactivate")
    public ResponseEntity<OperationalRegulationResponse> deactivate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.deactivateRegulation(regulationId));
    }

    @Operation(summary = "Danh sách quy định hiệu lực", description = "Lấy danh sách các quy định vận hành hiện đang có hiệu lực")
    @GetMapping("/effective")
    public ResponseEntity<List<OperationalRegulationResponse>> getEffective() {
        return ResponseEntity.ok(
                regulationService.getEffectiveRegulations());
    }

    @Operation(summary = "Kiểm tra tính hợp lệ quy định", description = "Kiểm tra tính hợp lệ cấu hình của quy định vận hành")
    @PostMapping("/{regulationId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID regulationId) {
        regulationService.validateRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kiểm tra xung đột quy định", description = "Kiểm tra xung đột giữa quy định này với các quy định khác")
    @GetMapping("/{regulationId}/conflicts")
    public ResponseEntity<List<OperationalRegulationResponse>> validateConflict(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(
                regulationService.validateConflict(regulationId));
    }
}
