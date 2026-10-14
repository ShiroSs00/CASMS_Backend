package com.casms.activity.controller;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.dto.request.SupplierVerificationRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @PostMapping
    public ResponseEntity<Supplier> create(
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.create(request));
    }

    @Operation(summary = "Danh sách nhà cung cấp", description = "Lấy danh sách tất cả các nhà cung cấp dịch vụ trong hệ thống")
    @GetMapping
    public ResponseEntity<List<Supplier>> list() {
        return ResponseEntity.ok(supplierService.list());
    }

    @Operation(summary = "Chi tiết nhà cung cấp", description = "Xem thông tin chi tiết của một nhà cung cấp theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<Supplier> detail(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(supplierService.detail(id));
    }

    @Operation(summary = "Cập nhật thông tin nhà cung cấp", description = "Cập nhật thông tin chi tiết của nhà cung cấp (tên, thông tin liên hệ, giấy phép)")
    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.ok(
                supplierService.update(id, request)
        );
    }

    @Operation(summary = "Xóa nhà cung cấp chưa sử dụng", description = "Xóa nhà cung cấp nếu chưa liên kết với bất kỳ tour tham quan hay hợp đồng nào")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnused(
            @Parameter(description = "ID nhà cung cấp cần xóa", required = true) @PathVariable UUID id
    ) {
        supplierService.deleteUnused(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kích hoạt nhà cung cấp", description = "Chuyển trạng thái nhà cung cấp sang ACTIVE để có thể liên kết tour")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Supplier> activate(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                supplierService.activate(id)
        );
    }

    @Operation(summary = "Ngừng kích hoạt nhà cung cấp", description = "Chuyển trạng thái nhà cung cấp sang INACTIVE để tạm dừng sử dụng dịch vụ")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Supplier> deactivate(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                supplierService.deactivate(id)
        );
    }

    @Operation(summary = "Ghi nhận xác minh nhà cung cấp", description = "Ghi nhận lịch sử kiểm tra, đánh giá và xác minh năng lực nhà cung cấp bởi nhân viên")
    @PostMapping("/{id}/verification")
    public ResponseEntity<Supplier> recordVerification(
            @Parameter(description = "ID nhà cung cấp", required = true) @PathVariable UUID id,
            @Valid @RequestBody SupplierVerificationRequest request
    ) {
        /*
         * TODO: Lấy employeeId từ tài khoản đã xác thực
         * trong Spring Security, không lấy từ request của client.
         *
         * Thay dòng dưới bằng ID của nhân viên đăng nhập
         * theo cơ chế xác thực thực tế của CASMS.
         */
        UUID employeeId = getAuthenticatedEmployeeId();

        return ResponseEntity.ok(
                supplierService.recordVerification(
                        id, request, employeeId
                )
        );
    }

    @Operation(summary = "Kiểm tra tính hợp lệ nhà cung cấp", description = "Xác thực tính hợp lệ của hồ sơ và thông tin nhà cung cấp")
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validate(
            @Parameter(description = "ID nhà cung cấp cần kiểm tra", required = true) @PathVariable UUID id
    ) {
        supplierService.validateSupplier(id);

        return ResponseEntity.ok(Map.of(
                "message", "Supplier information is valid"
        ));
    }

    /*
     * Thay bằng cách lấy ID từ SecurityContext/JWT
     * theo cấu trúc xác thực của dự án.
     */
    private UUID getAuthenticatedEmployeeId() {
        throw new UnsupportedOperationException(
                "Implement using the project's authentication mechanism"
        );
    }
}
