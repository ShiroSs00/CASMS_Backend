package com.casms.identity.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.identity.service.EmployeeImportService;
import com.casms.identity.service.EmployeeImportService.Batch;
import com.casms.identity.service.EmployeeWorkbook;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/employees/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER') and hasAuthority('USER_CREATE')")
public class EmployeeImportController {
    private final EmployeeImportService service;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal CasmsPrincipal actor) {
        return download(service.template(actor), "CASMS_Employee_Import_Template.xlsx");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Batch> upload(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        if (file == null || file.isEmpty() || file.getSize() > EmployeeWorkbook.MAX_UPLOAD_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPLOYEE_XLSX_INVALID",
                    "File XLSX phải có dữ liệu và không vượt quá 2 MB.");
        }
        try {
            return ApiResponse.success(service.upload(file.getBytes(), actor));
        } catch (IOException failure) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPLOYEE_XLSX_INVALID", "Không đọc được file XLSX.");
        }
    }

    @GetMapping("/{batchId}")
    public ApiResponse<Batch> preview(@PathVariable UUID batchId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.read(batchId, actor));
    }

    @PostMapping("/{batchId}/confirm")
    public ApiResponse<Batch> confirm(@PathVariable UUID batchId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.confirm(batchId, actor));
    }

    @GetMapping("/{batchId}/result")
    public ResponseEntity<byte[]> result(@PathVariable UUID batchId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return download(service.resultFile(batchId, actor), "CASMS_Employee_Import_Result.xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(EmployeeWorkbook.XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
