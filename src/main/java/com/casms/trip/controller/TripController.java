package com.casms.trip.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.dto.request.TripDayManageRequest;
import com.casms.trip.dto.response.TripDayManageResponse;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.dto.request.ScheduleVersionCreateRequest;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripUpdateRequest;
import com.casms.trip.dto.response.TripDetailResponse;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
import com.casms.trip.dto.response.TripScheduleVersionSummaryResponse;
import com.casms.trip.dto.response.TripSummaryResponse;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.dto.response.TripReviewQueueItemResponse;
import com.casms.trip.service.IdempotencyService;
import com.casms.trip.service.TripScheduleVersionService;
import com.casms.trip.service.TripService;
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

@Tag(name = "Trip Planning Management", description = "Quản lý định tuyến và khung kế hoạch Trip")
@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TripScheduleVersionService versionService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Hàng đợi duyệt lịch trình (Review Queue)", description = "Lấy danh sách các phiên bản lịch trình đang ở trạng thái IN_REVIEW chờ duyệt, hỗ trợ phân trang và sắp xếp.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @GetMapping("/review-queue")
    public ApiResponse<Page<TripReviewQueueItemResponse>> getReviewQueue(Pageable pageable) {
        return ApiResponse.success(versionService.getReviewQueue(pageable));
    }

    @Operation(summary = "Tạo mới Trip", description = "Khởi tạo khung Trip với mã TR-xxxx, tự động tạo Version 1 và tiền sinh N ngày tương đối (1..N). Yêu cầu Idempotency-Key header.")
    @PreAuthorize("hasAuthority('TRIP_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<TripDetailResponse> createTrip(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TripCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Header 'Idempotency-Key' là bắt buộc khi tạo Trip");
        }
        UUID actorId = principal != null ? principal.getUserId() : null;
        TripDetailResponse response = idempotencyService.execute(
                idempotencyKey, actorId, "CREATE_TRIP", request,
                () -> tripService.createTrip(request, actorId));
        return ApiResponse.success("Created Trip successfully", response);
    }

    @Operation(summary = "Danh sách Trip", description = "Tra cứu danh sách Trip có phân trang và lọc theo trạng thái, nhóm thời lượng, phạm vi, từ khóa tìm kiếm và lưu trữ")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<Page<TripSummaryResponse>> listTrips(
            @Parameter(description = "Lọc theo trạng thái Trip") @RequestParam(required = false) TripStatus status,
            @Parameter(description = "Lọc theo nhóm thời lượng (SHORT, LONG)") @RequestParam(required = false) TripDurationGroup durationGroup,
            @Parameter(description = "Lọc theo phạm vi (DOMESTIC, INTERNATIONAL)") @RequestParam(required = false) String plannedScope,
            @Parameter(description = "Tìm kiếm theo mã TR hoặc tên Trip") @RequestParam(required = false) String search,
            @Parameter(description = "Bao gồm các Trip đã lưu trữ (mặc định false)") @RequestParam(required = false, defaultValue = "false") Boolean includeArchived,
            Pageable pageable) {
        if (durationGroup == null && plannedScope == null && search == null && (includeArchived == null || !includeArchived)) {
            return ApiResponse.success(tripService.listTrips(status, pageable));
        }
        return ApiResponse.success(tripService.listTrips(status, durationGroup, plannedScope, search, includeArchived, pageable));
    }

    @Operation(summary = "Quản lý ngày an toàn (Thêm/Xóa ngày)", description = "Thêm hoặc xóa ngày an toàn trong giao dịch duy nhất với kiểm tra dual revision, giới hạn thời lượng theo nhóm, và kiểm tra tham chiếu.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{tripId}/versions/{versionId}/days/manage")
    public ApiResponse<TripDayManageResponse> manageDays(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "ID của Version", required = true) @PathVariable UUID versionId,
            @Valid @RequestBody TripDayManageRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Managed days successfully", tripService.manageDays(tripId, versionId, request, actorId));
    }

    @Operation(summary = "Chi tiết Trip", description = "Lấy chi tiết Trip kèm danh sách các phiên bản lịch trình mẫu")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> getTripDetail(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId) {
        return ApiResponse.success(tripService.getTripDetail(tripId));
    }

    @Operation(summary = "Chỉnh sửa bản nháp Trip (PATCH)", description = "Chỉnh sửa khung Trip qua PATCH với expectedRevision và kiểm tra khóa cấu trúc khi đã xuất bản.")
    @PreAuthorize("hasAuthority('TRIP_UPDATE')")
    @PatchMapping("/{tripId}/draft")
    public ApiResponse<TripDetailResponse> patchTripDraft(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Valid @RequestBody TripDraftPatchRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Patched Trip successfully", tripService.patchTripDraft(tripId, request, actorId));
    }

    @Operation(summary = "Cập nhật Trip (PUT)", description = "Cập nhật thông tin Trip toàn diện. Lưu ý: số ngày/đêm bị khóa vĩnh viễn khi đã có phiên bản được PUBLISHED")
    @PreAuthorize("hasAuthority('TRIP_UPDATE')")
    @PutMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> updateTrip(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Valid @RequestBody TripUpdateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Updated Trip successfully", tripService.updateTrip(tripId, request, actorId));
    }

    @Operation(summary = "Danh sách phiên bản lịch trình của Trip", description = "Lấy danh sách tóm tắt metadata các phiên bản lịch trình của Trip")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{tripId}/schedule-versions")
    public ApiResponse<List<TripScheduleVersionSummaryResponse>> getScheduleVersions(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId) {
        return ApiResponse.success(tripService.getScheduleVersions(tripId));
    }

    @Operation(summary = "Tạo phiên bản lịch trình mới cho Trip", description = "Tạo phiên bản lịch trình DRAFT tiếp theo với N khung ngày (tùy chọn nhân bản từ sourceVersionId cùng Trip)")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{tripId}/schedule-versions")
    public ApiResponse<TripScheduleVersionDetailResponse> createScheduleVersion(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @RequestBody(required = false) ScheduleVersionCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Created schedule version successfully",
                tripService.createScheduleVersion(tripId, request, actorId));
    }

    @Operation(summary = "Nhân bản Trip (Clone)", description = "Nhân bản toàn bộ Trip sang Trip mới ở trạng thái DRAFT. Yêu cầu Idempotency-Key header.")
    @PreAuthorize("hasAuthority('TRIP_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{tripId}/clone")
    public ApiResponse<TripDetailResponse> cloneTrip(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "ID của Trip gốc", required = true) @PathVariable UUID tripId,
            @Parameter(description = "ID của phiên bản nguồn (tùy chọn)") @RequestParam(required = false) UUID sourceVersionId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Header 'Idempotency-Key' là bắt buộc khi clone Trip");
        }
        UUID actorId = principal != null ? principal.getUserId() : null;
        String payloadKey = tripId + (sourceVersionId != null ? ":" + sourceVersionId : "");
        TripDetailResponse response = idempotencyService.execute(
                idempotencyKey, actorId, "CLONE_TRIP", payloadKey,
                () -> tripService.cloneTrip(tripId, sourceVersionId, actorId));
        return ApiResponse.success("Cloned Trip successfully", response);
    }

    @Operation(summary = "Lưu trữ Trip", description = "Chuyển trạng thái Trip sang ARCHIVED kèm kiểm tra expectedRevision và chặn nếu có bản IN_REVIEW")
    @PreAuthorize("hasAuthority('TRIP_DELETE')")
    @PostMapping("/{tripId}/archive")
    public ApiResponse<TripDetailResponse> archiveTrip(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @RequestBody(required = false) com.casms.trip.dto.request.TripArchiveRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Archived Trip successfully", tripService.archiveTrip(tripId, request, actorId));
    }

    @Operation(summary = "Hủy bản nháp lịch trình dở dang (Cancel Draft)", description = "Hủy bỏ bản nháp dở dang theo tripId và versionNumber, giải phóng trạng thái chưa hoàn tất của Trip.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{tripId}/versions/{versionNumber}/cancel")
    public ApiResponse<TripScheduleVersionDetailResponse> cancelDraft(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @RequestBody(required = false) com.casms.trip.dto.request.VersionCancelRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Cancelled version draft successfully",
                versionService.cancelDraft(tripId, versionNumber, request, actorId));
    }

    @Operation(summary = "Lịch sử kiểm toán Trip", description = "Tra cứu nhật ký audit events của Trip (hỗ trợ phân trang tùy chọn khi truyền page, size); không trả credentials hoặc PII không cần thiết.")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{tripId}/history")
    public ApiResponse<?> getTripHistory(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int p = page != null ? page : 0;
            int s = size != null ? size : 20;
            return ApiResponse.success(tripService.getTripHistory(tripId, PageRequest.of(p, s)));
        }
        return ApiResponse.success(tripService.getTripHistory(tripId));
    }
}
