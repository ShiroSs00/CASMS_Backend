package com.casms.activity.controller;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.dto.request.SupplierVerificationRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.service.SupplierService;
import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.identity.entity.User;
import com.casms.security.principal.CasmsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Supplier Management", description = "Quản lý nhà cung cấp dịch vụ và đối tác tour tham quan")
@RestController
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(summary = "Tạo nhà cung cấp mới", description = "Đăng ký thông tin nhà cung cấp dịch vụ mới vào hệ thống")
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<Supplier>> create(
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierService.create(request)));
    }

    @Operation(summary = "Danh sách nhà cung cấp", description = "Lấy danh sách tất cả các nhà cung cấp dịch vụ trong hệ thống")
    @PreAuthorize("hasAuthority('SUPPLIER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Supplier>>> list() {
        return ResponseEntity.ok(ApiResponse.success(supplierService.list()));
    }

    @Operation(summary = "Chi tiết nhà cung cấp", description = "Xem thông tin chi tiết của một nhà cung cấp theo ID")
    @PreAuthorize("hasAuthority('SUPPLIER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Supplier>> detail(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.detail(id)));
    }

    @Operation(summary = "Cập nhật thông tin nhà cung cấp", description = "Cập nhật thông tin chi tiết của nhà cung cấp (tên, thông tin liên hệ, giấy phép)")
    @PreAuthorize("hasAuthority('SUPPLIER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Supplier>> update(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(supplierService.update(id, request))
        );
    }

    @Operation(summary = "Xóa nhà cung cấp chưa sử dụng", description = "Xóa nhà cung cấp nếu chưa liên kết với bất kỳ tour tham quan hay hợp đồng nào")
    @PreAuthorize("hasAuthority('SUPPLIER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUnused(
            @Parameter(description = "ID nhà cung cấp cần xóa", required = true) @PathVariable UUID id
    ) {
        supplierService.deleteUnused(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted unused supplier successfully", null));
    }

    @Operation(summary = "Kích hoạt nhà cung cấp", description = "Chuyển trạng thái nhà cung cấp sang ACTIVE để có thể liên kết tour")
    @PreAuthorize("hasAuthority('SUPPLIER_STATUS_CHANGE')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Supplier>> activate(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(supplierService.activate(id))
        );
    }

    @Operation(summary = "Ngừng kích hoạt nhà cung cấp", description = "Chuyển trạng thái nhà cung cấp sang INACTIVE để tạm dừng sử dụng dịch vụ")
    @PreAuthorize("hasAuthority('SUPPLIER_STATUS_CHANGE')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Supplier>> deactivate(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(supplierService.deactivate(id))
        );
    }

    @Operation(summary = "Ghi nhận xác minh nhà cung cấp", description = "Ghi nhận lịch sử kiểm tra, đánh giá và xác minh năng lực nhà cung cấp bởi nhân viên")
    @PreAuthorize("hasAuthority('SUPPLIER_VERIFY')")
    @PostMapping("/{id}/verification")
    public ResponseEntity<ApiResponse<Supplier>> recordVerification(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id,
            @Valid @RequestBody SupplierVerificationRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal
    ) {
        UUID employeeId = getAuthenticatedEmployeeId(principal);

        return ResponseEntity.ok(
                ApiResponse.success(supplierService.recordVerification(
                        id, request, employeeId
                ))
        );
    }

    @Operation(summary = "Kiểm tra tính hợp lệ nhà cung cấp", description = "Xác thực tính hợp lệ của hồ sơ và thông tin nhà cung cấp")
    @PreAuthorize("hasAuthority('SUPPLIER_VALIDATE')")
    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<Map<String, String>>> validate(
            @Parameter(description = "ID nhà cung cấp cần kiểm tra", required = true) @PathVariable UUID id
    ) {
        supplierService.validateSupplier(id);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "Supplier information is valid"
        )));
    }

    private UUID getAuthenticatedEmployeeId(CasmsPrincipal principal) {
        if (principal != null && principal.getUserId() != null) {
            return principal.getUserId();
        }
        return getAuthenticatedEmployeeId();
    }

    private UUID getAuthenticatedEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof CasmsPrincipal casmsPrincipal) {
                return casmsPrincipal.getUserId();
            } else if (principal instanceof User user) {
                return user.getUserId();
            } else if (principal instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu xác thực tài khoản nhân viên");
    }
}
