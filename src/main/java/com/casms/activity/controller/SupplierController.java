
package com.casms.activity.controller;

import com.casms.activity.dto.request.SupplierRequest;
import com.casms.activity.dto.request.SupplierVerificationRequest;
import com.casms.activity.entity.Supplier;
import com.casms.activity.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<Supplier> create(
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> list() {
        return ResponseEntity.ok(supplierService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> detail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(supplierService.detail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.ok(
                supplierService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnused(
            @PathVariable UUID id
    ) {
        supplierService.deleteUnused(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Supplier> activate(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                supplierService.activate(id)
        );
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Supplier> deactivate(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                supplierService.deactivate(id)
        );
    }

    @PostMapping("/{id}/verification")
    public ResponseEntity<Supplier> recordVerification(
            @PathVariable UUID id,
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

    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validate(
            @PathVariable UUID id
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
