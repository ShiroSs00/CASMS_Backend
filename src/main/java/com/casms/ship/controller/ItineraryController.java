package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.ItineraryCreateRequest;
import com.casms.ship.dto.request.ItineraryUpdateDraftRequest;
import com.casms.ship.dto.response.ItineraryResponse;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.service.ItineraryService;
import com.casms.ship.service.PassengerItineraryService;
import com.casms.ship.dto.response.PassengerItineraryResponse;
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

@Tag(name = "Itinerary Management", description = "Quản lý lịch trình chuyến đi du thuyền")
@RestController
@RequestMapping("/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService service;
    private final PassengerItineraryService passengerItineraryService;

    @Operation(summary = "Lịch trình của tôi", description = "Trả lịch trình chuyến đi của các booking gắn với hành khách đã xác thực, kèm riêng các hoạt động đã đăng ký.")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @GetMapping("/mine")
    public ApiResponse<List<PassengerItineraryResponse>> mine(
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để xem lịch trình");
        }
        return ApiResponse.success(passengerItineraryService.mine(principal.getUserId()));
    }

    @Operation(summary = "Tạo lịch trình mới", description = "Tạo lịch trình mới cho một chuyến đi (mặc định trạng thái DRAFT)")
    @PreAuthorize("hasAuthority('ITINERARY_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ItineraryResponse> create(
            @RequestBody @Valid ItineraryCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để tạo lịch trình");
        }
        return ApiResponse.success(service.create(request, principal.getUserId()));
    }

    @Operation(summary = "Danh sách lịch trình", description = "Lấy danh sách các lịch trình (hỗ trợ phân trang)")
    @PreAuthorize("hasAnyAuthority('ITINERARY_READ', 'CRUISE_READ')")
    @GetMapping
    public ApiResponse<Page<ItineraryResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @Operation(summary = "Chi tiết lịch trình", description = "Lấy thông tin chi tiết của một lịch trình theo ID")
    @PreAuthorize("hasAnyAuthority('ITINERARY_READ', 'CRUISE_READ')")
    @GetMapping("/{itineraryId}")
    public ApiResponse<ItineraryResponse> detail(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.detail(itineraryId));
    }

    @Operation(summary = "Cập nhật bản nháp lịch trình", description = "Cập nhật thông tin lịch trình (chỉ khi lịch trình ở trạng thái DRAFT)")
    @PreAuthorize("hasAuthority('ITINERARY_UPDATE_DRAFT')")
    @PatchMapping("/{itineraryId}/draft")
    public ApiResponse<ItineraryResponse> updateDraft(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId,
            @RequestBody @Valid ItineraryUpdateDraftRequest request) {
        return ApiResponse.success(service.updateDraft(itineraryId, request));
    }

    @Operation(summary = "Xóa bản nháp lịch trình", description = "Xóa lịch trình ở trạng thái DRAFT và chưa có ngày nào")
    @PreAuthorize("hasAuthority('ITINERARY_DELETE_DRAFT')")
    @DeleteMapping("/{itineraryId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        service.deleteDraft(itineraryId);
        return ApiResponse.success("Deleted draft itinerary successfully", null);
    }

    @Operation(summary = "Kiểm tra tính khả thi", description = "Kiểm tra tính khả thi của lịch trình (khoảng ngày, ngày tuần tự, phù hợp với chuyến đi)")
    @PreAuthorize("hasAuthority('ITINERARY_VALIDATE')")
    @PostMapping("/{itineraryId}/validate-feasibility")
    public ApiResponse<Void> validateFeasibility(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        service.validateFeasibility(itineraryId);
        return ApiResponse.success("Feasibility validation passed", null);
    }

    @Operation(summary = "Gửi phê duyệt", description = "Gửi lịch trình từ DRAFT sang PENDING_APPROVAL")
    @PreAuthorize("hasAuthority('ITINERARY_SUBMIT')")
    @PostMapping("/{itineraryId}/submit")
    public ApiResponse<ItineraryResponse> submit(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.submit(itineraryId));
    }

    @Operation(summary = "Phê duyệt lịch trình", description = "Phê duyệt lịch trình từ PENDING_APPROVAL sang APPROVED")
    @PreAuthorize("hasAuthority('ITINERARY_APPROVE')")
    @PostMapping("/{itineraryId}/approve")
    public ApiResponse<ItineraryResponse> approve(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để phê duyệt lịch trình");
        }
        return ApiResponse.success(service.approve(itineraryId, principal.getUserId()));
    }

    @Operation(summary = "Từ chối lịch trình", description = "Từ chối lịch trình từ PENDING_APPROVAL sang REJECTED")
    @PreAuthorize("hasAuthority('ITINERARY_REJECT')")
    @PostMapping("/{itineraryId}/reject")
    public ApiResponse<ItineraryResponse> reject(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId,
            @Parameter(description = "Lý do từ chối", required = true) @RequestParam String reason,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để từ chối lịch trình");
        }
        return ApiResponse.success(service.reject(itineraryId, principal.getUserId(), reason));
    }

    @Operation(summary = "Mở lại bản nháp lịch trình", description = "Chuyển lịch trình từ REJECTED hoặc PENDING_APPROVAL về DRAFT để chỉnh sửa lại")
    @PreAuthorize("hasAuthority('ITINERARY_UPDATE_DRAFT')")
    @PostMapping("/{itineraryId}/return-to-draft")
    public ApiResponse<ItineraryResponse> returnToDraft(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.returnToDraft(itineraryId, actorId));
    }

    @Operation(summary = "Áp dụng thay đổi", description = "Áp dụng thay đổi cho lịch trình đã được phê duyệt")
    @PreAuthorize("hasAuthority('ITINERARY_APPLY_CHANGE')")
    @PostMapping("/{itineraryId}/apply-change")
    public ApiResponse<ItineraryResponse> applyChange(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId,
            @Parameter(description = "Mô tả thay đổi", required = true) @RequestParam String changeDescription) {
        return ApiResponse.success(service.applyChange(itineraryId, changeDescription));
    }

    @Operation(summary = "Kiểm tra cấu trúc lịch trình", description = "Kiểm tra cấu trúc lịch trình (có ít nhất 1 ngày, ngày liên tục, số thứ tự tuần tự)")
    @PreAuthorize("hasAuthority('ITINERARY_VALIDATE')")
    @PostMapping("/{itineraryId}/validate-structure")
    public ApiResponse<Void> validateStructure(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        service.validateStructure(itineraryId);
        return ApiResponse.success("Structure validation passed", null);
    }

    @Operation(summary = "Kiểm tra tính tuần tự ngày", description = "Kiểm tra các ngày trong lịch trình được đánh số tuần tự từ 1")
    @PreAuthorize("hasAuthority('ITINERARY_VALIDATE')")
    @PostMapping("/{itineraryId}/validate-sequence")
    public ApiResponse<Void> validateSequence(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        service.validateSequence(itineraryId);
        return ApiResponse.success("Sequence validation passed", null);
    }

    @Operation(summary = "Phát hành lịch trình", description = "Phát hành lịch trình từ APPROVED sang OPEN_FOR_SALE")
    @PreAuthorize("hasAuthority('ITINERARY_PUBLISH')")
    @PostMapping("/{itineraryId}/publish")
    public ApiResponse<ItineraryResponse> publish(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.publish(itineraryId));
    }

    @Operation(summary = "Hủy lịch trình", description = "Hủy lịch trình (không thể hủy lịch trình đã hoàn tất)")
    @PreAuthorize("hasAuthority('ITINERARY_CANCEL')")
    @PostMapping("/{itineraryId}/cancel")
    public ApiResponse<ItineraryResponse> cancel(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.cancel(itineraryId));
    }

    @Operation(summary = "Danh sách ngày trong lịch trình", description = "Lấy danh sách tất cả ngày trong một lịch trình")
    @PreAuthorize("hasAnyAuthority('ITINERARY_READ', 'CRUISE_READ')")
    @GetMapping("/{itineraryId}/days")
    public ApiResponse<List<ItineraryDayResponse>> listDays(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.listDays(itineraryId));
    }
}
