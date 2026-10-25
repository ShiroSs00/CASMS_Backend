package com.casms.trip.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.VersionReturnRequest;
import com.casms.trip.dto.request.VersionWithdrawRequest;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
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

import java.util.UUID;

@Tag(name = "Legacy Trip Schedule Version Endpoints", description = "Endpoints tương thích ngược cho phiên bản lịch trình theo versionNumber")
@RestController
@RequestMapping("/trips/{tripId}/versions")
@RequiredArgsConstructor
public class LegacyTripScheduleVersionController {

    private final TripScheduleVersionService versionService;

    @Operation(summary = "Lấy phiên bản mới nhất", description = "Lấy chi tiết phiên bản có số thứ tự lớn nhất của Trip")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/latest")
    public ApiResponse<TripScheduleVersionDetailResponse> getLatestVersionDetail(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId) {
        return ApiResponse.success(versionService.getLatestVersionDetail(tripId));
    }

    @Operation(summary = "Lấy chi tiết một phiên bản theo số thứ tự", description = "Lấy chi tiết một phiên bản lịch trình theo số thứ tự versionNumber")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/{versionNumber}")
    public ApiResponse<TripScheduleVersionDetailResponse> getVersionDetail(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber) {
        return ApiResponse.success(versionService.getVersionDetail(tripId, versionNumber));
    }

    @Operation(summary = "Lưu bản nháp lịch trình", description = "Lưu dự thảo lịch trình theo số thứ tự phiên bản.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PutMapping("/{versionNumber}/draft")
    public ApiResponse<TripScheduleVersionDetailResponse> saveDraft(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @Valid @RequestBody ScheduleDraftSaveRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Saved schedule draft successfully",
                versionService.saveDraft(tripId, versionNumber, request, actorId));
    }

    @Operation(summary = "Gửi phê duyệt phiên bản", description = "Gửi phiên bản từ DRAFT sang IN_REVIEW.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_SUBMIT')")
    @PostMapping("/{versionNumber}/submit")
    public ApiResponse<TripScheduleVersionDetailResponse> submitForReview(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Submitted version for review successfully",
                versionService.submitForReview(tripId, versionNumber, actorId));
    }

    @Operation(summary = "Trả về bản nháp", description = "Từ chối duyệt và trả phiên bản từ IN_REVIEW về DRAFT (bắt buộc kèm lý do từ chối)")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @PostMapping("/{versionNumber}/return-to-draft")
    public ApiResponse<TripScheduleVersionDetailResponse> returnToDraft(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @Valid @RequestBody VersionReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Returned version to draft successfully",
                versionService.returnToDraft(tripId, versionNumber, request, actorId));
    }

    @Operation(summary = "Phê duyệt xuất bản (Maker-Checker)", description = "Phê duyệt phiên bản từ IN_REVIEW sang PUBLISHED.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_REVIEW')")
    @PostMapping("/{versionNumber}/publish")
    public ApiResponse<TripScheduleVersionDetailResponse> publishVersion(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để phê duyệt xuất bản phiên bản");
        }
        return ApiResponse.success("Published version successfully",
                versionService.publishVersion(tripId, versionNumber, principal.getUserId()));
    }

    @Operation(summary = "Thu hồi phiên bản đang chờ duyệt (Withdraw from Review)", description = "Thu hồi phiên bản từ IN_REVIEW về DRAFT.")
    @PreAuthorize("hasAuthority('TRIP_PLAN_SUBMIT') or hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/{versionNumber}/withdraw")
    public ApiResponse<TripScheduleVersionDetailResponse> withdraw(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @Parameter(description = "Số thứ tự phiên bản", required = true) @PathVariable Integer versionNumber,
            @RequestBody(required = false) VersionWithdrawRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Withdrawn version from review successfully",
                versionService.withdraw(tripId, versionNumber, request, actorId));
    }

    @Operation(summary = "Tạo bản nháp phiên bản tiếp theo", description = "Nhân bản từ phiên bản đã xuất bản gần nhất sang bản nháp mới với versionNumber + 1")
    @PreAuthorize("hasAuthority('TRIP_PLAN_WRITE')")
    @PostMapping("/new-draft")
    public ApiResponse<TripScheduleVersionDetailResponse> createNextDraftVersion(
            @Parameter(description = "ID của Trip", required = true) @PathVariable UUID tripId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Created new draft version successfully",
                versionService.createNextDraftVersion(tripId, actorId));
    }
}
