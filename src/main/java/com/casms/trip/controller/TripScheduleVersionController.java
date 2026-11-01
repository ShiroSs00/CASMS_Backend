package com.casms.trip.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.dto.request.ActionRevisionRequest;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.TripDayManageRequest;
import com.casms.trip.dto.request.VersionCancelRequest;
import com.casms.trip.dto.request.VersionReturnRequest;
import com.casms.trip.dto.request.VersionWithdrawRequest;
import com.casms.trip.dto.response.TripAuditResponse;
import com.casms.trip.dto.response.TripDayManageResponse;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
import com.casms.trip.dto.response.ValidationReport;
import com.casms.trip.dto.response.EligibleTripSourceResponse;
import com.casms.trip.service.TripScheduleVersionService;
import com.casms.trip.service.TripService;
import com.casms.trip.dto.response.TripReviewQueueItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final TripService tripService;

    @Operation(summary = "Lấy chi tiết một phiên bản", description = "Lấy toàn bộ cây dữ liệu của phiên bản lịch trình (days, stops, entries)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{versionId}")
    public ApiResponse<TripScheduleVersionDetailResponse> getVersionDetail(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId) {
        return ApiResponse.success(versionService.getVersionDetailById(versionId));
    }

    @Operation(summary = "Lưu toàn bộ nội dung cây lịch trình", description = "Atomic save tree: days[] + stops[] + entries[]. Đối chiếu expectedRevision. SERVING_WINDOW lịch sử bị bỏ khỏi payload vẫn được giữ nguyên; tạo/sửa hoặc đổi kind trả 422 LEGACY_SERVING_WINDOW_READ_ONLY. Xóa stop đang được legacy tham chiếu trả 409 LEGACY_TEMPLATE_REFERENCE_LOCKED. Giao dịch bị từ chối không đổi dữ liệu/revision.")
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

    @Operation(summary = "CRM chốt mẫu lịch trình", description = "Chỉ CRUISE_MANAGER; DRAFT, expectedRevision phải khớp; BE kiểm tra lại cấu trúc và thời lượng. NEEDS_SAILING_DATE là kiểm tra hoãn, không chặn chốt mẫu. Kết quả PUBLISHED bất biến; không phải OPS duyệt Cruise Plan.")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('TRIP_PLAN_WRITE') and hasAuthority('TRIP_PLAN_VALIDATE')")
    @PostMapping("/{versionId}/finalize")
    public ApiResponse<TripScheduleVersionDetailResponse> finalizeTemplate(
            @PathVariable UUID versionId, @RequestBody ActionRevisionRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(versionService.finalizeVersion(versionId, request.getExpectedRevision(),
                principal != null ? principal.getUserId() : null));
    }

    @Operation(summary = "Template Review đã dừng", deprecated = true, description = "Endpoint tương thích cũ; luôn trả 410 TEMPLATE_REVIEW_RETIRED sau kiểm tra xác thực/quyền, không đổi trạng thái hoặc revision. CRM dùng endpoint finalize; OPS xét duyệt kế hoạch chuyến qua /cruise-plans.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "TEMPLATE_REVIEW_RETIRED — dữ liệu lịch sử giữ nguyên")
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

    @Operation(summary = "Template Review đã dừng", deprecated = true, description = "Endpoint tương thích cũ; luôn trả 410 TEMPLATE_REVIEW_RETIRED sau kiểm tra xác thực/quyền, không đổi trạng thái hoặc revision. CRM dùng endpoint finalize; OPS xét duyệt kế hoạch chuyến qua /cruise-plans.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "TEMPLATE_REVIEW_RETIRED — dữ liệu lịch sử giữ nguyên")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @PostMapping("/{versionId}/return")
    public ApiResponse<TripScheduleVersionDetailResponse> returnToDraft(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestBody(required = false) VersionReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Returned version to draft successfully",
                versionService.returnToDraftById(versionId, request, actorId));
    }

    @Operation(summary = "Hủy bản nháp dở dang (Cancel Draft)", description = "Hủy bỏ bản nháp dở dang, giải phóng Trip để tạo bản nháp mới mà không bị kẹt CONCURRENT_UNFINISHED_VERSION, giữ dấu vết audit.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{versionId}/cancel")
    public ApiResponse<TripScheduleVersionDetailResponse> cancelDraft(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestBody(required = false) VersionCancelRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Cancelled version draft successfully",
                versionService.cancelDraftById(versionId, request, actorId));
    }

    @Operation(summary = "Template Review đã dừng", deprecated = true, description = "Endpoint tương thích cũ; luôn trả 410 TEMPLATE_REVIEW_RETIRED sau kiểm tra xác thực/quyền, không đổi trạng thái hoặc revision. CRM dùng endpoint finalize; OPS xét duyệt kế hoạch chuyến qua /cruise-plans.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "TEMPLATE_REVIEW_RETIRED — dữ liệu lịch sử giữ nguyên")
    @PreAuthorize("hasAuthority('TRIP_PLAN_SUBMIT') or hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{versionId}/withdraw")
    public ApiResponse<TripScheduleVersionDetailResponse> withdraw(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestBody(required = false) VersionWithdrawRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Withdrawn version from review successfully",
                versionService.withdrawById(versionId, request, actorId));
    }

    @Operation(summary = "Template Review đã dừng", deprecated = true, description = "Endpoint tương thích cũ; luôn trả 410 TEMPLATE_REVIEW_RETIRED sau kiểm tra xác thực/quyền, không đổi trạng thái hoặc revision. CRM dùng endpoint finalize; OPS xét duyệt kế hoạch chuyến qua /cruise-plans.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "TEMPLATE_REVIEW_RETIRED — dữ liệu lịch sử giữ nguyên")
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

    @Operation(summary = "Tra cứu hồ sơ Template Review lịch sử", deprecated = true, description = "Chỉ đọc dữ liệu IN_REVIEW lịch sử để tương thích; không phải hàng đợi thao tác OPS trong workflow hiện hành. OPS xét duyệt kế hoạch chuyến tại /cruise-plans.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @GetMapping("/review-queue")
    public ApiResponse<Page<TripReviewQueueItemResponse>> getReviewQueue(Pageable pageable) {
        return ApiResponse.success(versionService.getReviewQueue(pageable));
    }

    @Operation(summary = "Lịch sử kiểm toán phiên bản", description = "Tra cứu nhật ký audit events của phiên bản (hỗ trợ phân trang tùy chọn khi truyền page, size); không trả credentials hoặc PII không cần thiết.")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{versionId}/history")
    public ApiResponse<?> getVersionHistory(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int p = page != null ? page : 0;
            int s = size != null ? size : 20;
            return ApiResponse.success(versionService.getVersionHistory(versionId, PageRequest.of(p, s)));
        }
        return ApiResponse.success(versionService.getVersionHistory(versionId));
    }

    @Operation(summary = "Quản lý ngày an toàn (Thêm/Xóa ngày theo versionId)", description = "Thêm hoặc xóa ngày an toàn trong giao dịch duy nhất với kiểm tra dual revision, giới hạn thời lượng theo nhóm, và kiểm tra tham chiếu.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{versionId}/days/manage")
    public ApiResponse<TripDayManageResponse> manageDays(
            @Parameter(description = "ID của phiên bản lịch trình", required = true) @PathVariable UUID versionId,
            @Valid @RequestBody TripDayManageRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        TripScheduleVersionDetailResponse detail = versionService.getVersionDetailById(versionId);
        return ApiResponse.success("Managed days successfully",
                tripService.manageDays(detail.getTripId(), versionId, request, actorId));
    }

    @Operation(summary = "Truy vấn phiên bản mẫu Trip đủ điều kiện sử dụng (C01)",
               description = "Tìm kiếm các phiên bản TripScheduleVersion đã xuất bản (PUBLISHED) và Trip còn hiệu lực (không ARCHIVED) để tạo chuyến khởi hành Cruise Draft.")
    @PreAuthorize("hasAuthority('CRUISE_CREATE') or hasAuthority('TRIP_READ')")
    @GetMapping("/eligible-sources")
    public ApiResponse<Page<EligibleTripSourceResponse>> getEligibleSources(
            @Parameter(description = "Lọc theo ID Trip mẫu") @RequestParam(required = false) UUID tripId,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã Trip") @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ApiResponse.success(versionService.getEligibleSources(tripId, keyword, pageable));
    }
}
