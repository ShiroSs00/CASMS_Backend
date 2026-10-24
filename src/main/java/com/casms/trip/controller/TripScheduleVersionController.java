package com.casms.trip.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.dto.request.ActionRevisionRequest;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.VersionReturnRequest;
import com.casms.trip.dto.response.TripAuditResponse;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
import com.casms.trip.dto.response.ValidationReport;
import com.casms.trip.service.TripScheduleVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Trip Schedule Version Management", description = "Quản lý phiên bản mẫu lịch trình tương đối theo hợp đồng API FE-BE")
@RestController
@RequestMapping("/trip-schedule-versions")
@RequiredArgsConstructor
public class TripScheduleVersionController {

    private final TripScheduleVersionService versionService;

    @Operation(summary = "Lấy chi tiết một phiên bản", description = "Lấy toàn bộ cây dữ liệu của phiên bản lịch trình (days, stops, entries)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{versionId}")
    public ApiResponse<TripScheduleVersionDetailResponse> getVersionDetail(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId) {
        return ApiResponse.success(versionService.getVersionDetailById(versionId));
    }

    @Operation(summary = "Lưu toàn bộ nội dung cây lịch trình", description = "Atomic save tree: days[] + stops[] + entries[]. Đối chiếu optimistic lock qua expectedRevision.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PutMapping("/{versionId}/content")
    public ApiResponse<TripScheduleVersionDetailResponse> saveContent(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @Valid @RequestBody ScheduleDraftSaveRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Saved schedule version content successfully",
                versionService.saveDraftById(versionId, request, actorId));
    }

    @Operation(summary = "Thẩm định tính khả thi của phiên bản", description = "Kiểm tra toàn diện tính khả thi của lịch trình và trả về ValidationReport, không thay đổi trạng thái phiên bản.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_VALIDATE')")
    @PostMapping("/{versionId}/validate")
    public ApiResponse<ValidationReport> validateVersion(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId) {
        return ApiResponse.success(versionService.validateVersion(versionId));
    }

    @Operation(summary = "Gửi phê duyệt phiên bản", description = "Chuyển trạng thái từ DRAFT sang IN_REVIEW sau khi qua khâu thẩm định tự động.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_SUBMIT')")
    @PostMapping("/{versionId}/submit")
    public ApiResponse<TripScheduleVersionDetailResponse> submitForReview(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestBody(required = false) ActionRevisionRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        Long expectedRevision = request != null ? request.getExpectedRevision() : null;
        return ApiResponse.success("Submitted version for review successfully",
                versionService.submitForReviewById(versionId, expectedRevision, actorId));
    }

    @Operation(summary = "Trả về bản nháp", description = "Người đánh giá từ chối duyệt và trả về DRAFT (bắt buộc kèm lý do từ chối)")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @PostMapping("/{versionId}/return")
    public ApiResponse<TripScheduleVersionDetailResponse> returnToDraft(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @Valid @RequestBody VersionReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Returned version to draft successfully",
                versionService.returnToDraftById(versionId, request, actorId));
    }

    @Operation(summary = "Phê duyệt xuất bản (Maker-Checker)", description = "Phê duyệt phiên bản từ IN_REVIEW sang PUBLISHED. Tự động kiểm tra thẩm định lại và quy tắc Maker-Checker.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @PostMapping("/{versionId}/publish")
    public ApiResponse<TripScheduleVersionDetailResponse> publishVersion(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestBody(required = false) ActionRevisionRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để phê duyệt xuất bản phiên bản");
        }
        Long expectedRevision = request != null ? request.getExpectedRevision() : null;
        return ApiResponse.success("Published version successfully",
                versionService.publishVersionById(versionId, expectedRevision, principal.getUserId()));
    }

    @Operation(summary = "Lịch sử kiểm toán phiên bản", description = "Tra cứu nhật ký audit events của phiên bản; không trả credentials hoặc PII không cần thiết.")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{versionId}/history")
    public ApiResponse<List<TripAuditResponse>> getVersionHistory(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId) {
        return ApiResponse.success(versionService.getVersionHistory(versionId));
    }
}
