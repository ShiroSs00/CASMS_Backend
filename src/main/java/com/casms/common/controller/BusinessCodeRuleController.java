package com.casms.common.controller;

import com.casms.common.dto.BusinessCodeRuleResponse;
import com.casms.common.dto.BusinessCodeRuleUpdateRequest;
import com.casms.common.dto.CodePreviewRequest;
import com.casms.common.dto.CodePreviewResponse;
import com.casms.common.response.ApiResponse;
import com.casms.common.service.BusinessCodeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Business Code Rules", description = "Quản lý và cấu hình định dạng sinh mã nghiệp vụ tự động")
@RestController
@RequestMapping("/system/business-code-rules")
@RequiredArgsConstructor
@Validated
public class BusinessCodeRuleController {

    private final BusinessCodeRuleService ruleService;

    @Operation(summary = "Lấy danh sách tất cả quy tắc sinh mã", description = "Trả về danh sách cấu hình mẫu mã cho tất cả các thực thể (TRIP, SHIP, CRUISE, EXCURSION, BOOKING...)")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<List<BusinessCodeRuleResponse>> getAllRules() {
        return ApiResponse.success(ruleService.getAllRules());
    }

    @Operation(summary = "Lấy chi tiết quy tắc theo Entity", description = "Trả về thông tin chi tiết, metadata Token hỗ trợ và context mẫu để hiển thị giao diện biên tập")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TRIP_READ')")
    @GetMapping("/{entityType}")
    public ApiResponse<BusinessCodeRuleResponse> getRuleByEntityType(@PathVariable String entityType) {
        return ApiResponse.success(ruleService.getRuleByEntityType(entityType));
    }

    @Operation(summary = "Xem trước mã tức thì (Live Preview)", description = "Ghép chuỗi pattern với context mẫu và sinh mã thử nghiệm mà không làm nhảy số sequence trong DB")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TRIP_PLAN_WRITE') or hasAuthority('TRIP_READ')")
    @PostMapping("/preview")
    public ApiResponse<CodePreviewResponse> previewCode(@Valid @RequestBody CodePreviewRequest request) {
        return ApiResponse.success(ruleService.previewCode(request));
    }

    @Operation(summary = "Cập nhật cấu hình định dạng mã", description = "Lưu pattern và chiến lược sequence mới cho entity. Bắt buộc pattern phải chứa token {SEQ:n}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TRIP_UPDATE')")
    @PutMapping("/{entityType}")
    public ApiResponse<BusinessCodeRuleResponse> updateRule(
            @PathVariable String entityType,
            @Valid @RequestBody BusinessCodeRuleUpdateRequest request) {
        return ApiResponse.success("Cập nhật quy tắc sinh mã thành công", ruleService.updateRule(entityType, request));
    }
}
