package com.casms.trip.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.dto.request.ScheduleVersionCreateRequest;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripUpdateRequest;
import com.casms.trip.dto.response.TripDetailResponse;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
import com.casms.trip.dto.response.TripScheduleVersionSummaryResponse;
import com.casms.trip.dto.response.TripSummaryResponse;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.service.IdempotencyService;
import com.casms.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final IdempotencyService idempotencyService;

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

    @Operation(summary = "Danh sách Trip", description = "Tra cứu danh sách Trip có phân trang và lọc theo trạng thái")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<Page<TripSummaryResponse>> listTrips(
            @Parameter(description = "Lọc theo trạng thái Trip") @RequestParam(required = false) TripStatus status,
            Pageable pageable) {
        return ApiResponse.success(tripService.listTrips(status, pageable));
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

    @Operation(summary = "Lưu trữ Trip", description = "Chuyển trạng thái Trip sang ARCHIVED")
    @PreAuthorize("hasAuthority('TRIP_DELETE')")
    @PostMapping("/{tripId}/archive")
    public ApiResponse<TripDetailResponse> archiveTrip(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Archived Trip successfully", tripService.archiveTrip(tripId, actorId));
    }
}
