package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.OperationalRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.service.OperationalRegulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAuthority('REGULATION_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<OperationalRegulationResponse>> create(
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(regulationService.createRegulation(request)));
    }

    @Operation(summary = "Danh sách quy định", description = "Lấy toàn bộ danh sách quy định vận hành")
    @PreAuthorize("hasAuthority('REGULATION_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationalRegulationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(regulationService.listRegulations()));
    }

    @Operation(summary = "Chi tiết quy định", description = "Lấy thông tin chi tiết của một quy định vận hành theo ID")
    @PreAuthorize("hasAuthority('REGULATION_READ')")
    @GetMapping("/{regulationId}")
    public ResponseEntity<ApiResponse<OperationalRegulationResponse>> detail(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.getRegulationDetail(regulationId)));
    }

    @Operation(summary = "Cập nhật quy định", description = "Chỉnh sửa nội dung, tham số của quy định vận hành")
    @PreAuthorize("hasAuthority('REGULATION_UPDATE')")
    @PutMapping("/{regulationId}")
    public ResponseEntity<ApiResponse<OperationalRegulationResponse>> update(
            @PathVariable UUID regulationId,
            @Valid @RequestBody OperationalRegulationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.updateRegulation(regulationId, request)));
    }

    @Operation(summary = "Xóa quy định", description = "Xóa quy định vận hành chưa được sử dụng")
    @PreAuthorize("hasAuthority('REGULATION_DELETE')")
    @DeleteMapping("/{regulationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID regulationId) {
        regulationService.deleteUnusedRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kích hoạt quy định", description = "Chuyển trạng thái quy định sang hiệu lực (ACTIVE)")
    @PreAuthorize("hasAuthority('REGULATION_ACTIVATE')")
    @PatchMapping("/{regulationId}/activate")
    public ResponseEntity<ApiResponse<OperationalRegulationResponse>> activate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.activateRegulation(regulationId)));
    }

    @Operation(summary = "Hủy kích hoạt quy định", description = "Chuyển trạng thái quy định sang ngưng hiệu lực (INACTIVE)")
    @PreAuthorize("hasAuthority('REGULATION_DEACTIVATE')")
    @PatchMapping("/{regulationId}/deactivate")
    public ResponseEntity<ApiResponse<OperationalRegulationResponse>> deactivate(
            @PathVariable UUID regulationId) {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.deactivateRegulation(regulationId)));
    }

    @Operation(summary = "Danh sách quy định hiệu lực", description = "Lấy danh sách các quy định vận hành hiện đang có hiệu lực")
    @PreAuthorize("hasAuthority('REGULATION_READ')")
    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<List<OperationalRegulationResponse>>> getEffective() {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.getEffectiveRegulations()));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ quy định", description = "Kiểm tra tính hợp lệ cấu hình của quy định vận hành")
    @PreAuthorize("hasAuthority('REGULATION_VALIDATE')")
    @PostMapping("/{regulationId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID regulationId) {
        regulationService.validateRegulation(regulationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kiểm tra xung đột quy định", description = "Kiểm tra xung đột giữa quy định này với các quy định khác và trả kết luận chính thức")
    @PreAuthorize("hasAuthority('REGULATION_VALIDATE')")
    @GetMapping("/{regulationId}/conflicts")
    public ResponseEntity<ApiResponse<RegulationConflictResponse>> validateConflict(
            @PathVariable UUID regulationId,
            @RequestParam(required = false, defaultValue = "ACTIVATE") String context) {
        return ResponseEntity.ok(ApiResponse.success(
                regulationService.getConflictVerdict(regulationId, context)));
    }
}
