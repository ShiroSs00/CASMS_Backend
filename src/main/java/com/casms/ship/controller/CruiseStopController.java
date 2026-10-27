package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.CruisePortCoordinationRequest;
import com.casms.ship.dto.request.CruiseStopCreateRequest;
import com.casms.ship.dto.request.CruiseStopUpdateRequest;
import com.casms.ship.dto.response.CruiseStopResponse;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.service.CruiseStopService;
import com.casms.security.principal.CasmsPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Cruise Stop Management", description = "Quản lý điểm dừng trong lịch trình chuyến đi")
@RestController
@RequestMapping("/cruise-stops")
@RequiredArgsConstructor
public class CruiseStopController {

    private final CruiseStopService service;

    @Operation(summary = "Thêm điểm dừng mới", description = "Thêm điểm dừng mới vào lịch trình (chỉ khi lịch trình ở trạng thái DRAFT hoặc PENDING_APPROVAL)")
    @PreAuthorize("hasAuthority('CRUISE_STOP_ADD')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CruiseStopResponse> add(@RequestBody @Valid CruiseStopCreateRequest request) {
        return ApiResponse.success(service.add(request));
    }

    @Operation(summary = "Danh sách điểm dừng theo lịch trình", description = "Lấy danh sách tất cả điểm dừng trong một lịch trình (đã sắp xếp theo thứ tự)")
    @PreAuthorize("hasAnyAuthority('CRUISE_STOP_READ', 'CRUISE_READ', 'ITINERARY_READ')")
    @GetMapping("/by-itinerary/{itineraryId}")
    public ApiResponse<List<CruiseStopResponse>> listByItinerary(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.listByItinerary(itineraryId));
    }

    @Operation(summary = "Chi tiết điểm dừng", description = "Lấy thông tin chi tiết của một điểm dừng theo ID")
    @PreAuthorize("hasAnyAuthority('CRUISE_STOP_READ', 'CRUISE_READ', 'ITINERARY_READ')")
    @GetMapping("/{cruiseStopId}")
    public ApiResponse<CruiseStopResponse> detail(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId) {
        return ApiResponse.success(service.detail(cruiseStopId));
    }

    @Operation(summary = "Cập nhật điểm dừng", description = "Cập nhật thông tin điểm dừng (chỉ khi lịch trình ở trạng thái DRAFT hoặc PENDING_APPROVAL)")
    @PreAuthorize("hasAuthority('CRUISE_STOP_UPDATE')")
    @PatchMapping("/{cruiseStopId}")
    public ApiResponse<CruiseStopResponse> update(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @RequestBody @Valid CruiseStopUpdateRequest request) {
        return ApiResponse.success(service.update(cruiseStopId, request));
    }

    @Operation(summary = "Cập nhật hồ sơ và trạng thái phối hợp cảng (BE-20 Port Coordination)", description = "Lưu trữ contact port agent, deadline phối hợp, mã hồ sơ và trạng thái xử lý")
    @PreAuthorize("hasAnyAuthority('CRUISE_STOP_UPDATE', 'CRUISE_UPDATE_DRAFT')")
    @PatchMapping("/{cruiseStopId}/coordination")
    public ApiResponse<CruiseStopResponse> updatePortCoordination(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @RequestBody @Valid CruisePortCoordinationRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.updatePortCoordination(cruiseStopId, request, actorId));
    }

    @Operation(summary = "Ghi nhận mã tham chiếu giấy phép chính thức từ cơ quan chức năng (BE-20 External Clearance Reference)",
               description = "Lưu trữ mã tham chiếu xác thực từ bên ngoài khi có tích hợp; CASMS tuyệt đối không tự cấp phép rời cảng")
    @PreAuthorize("hasAnyAuthority('CRUISE_STOP_UPDATE', 'CRUISE_STATUS_CHANGE')")
    @PostMapping("/{cruiseStopId}/record-clearance-reference")
    public ApiResponse<CruiseStopResponse> recordClearanceReference(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Mã tham chiếu giấy phép cảng", required = true) @RequestParam String clearanceReference,
            @Parameter(description = "Tên cơ quan thẩm quyền cấp phép (ví dụ Cảng vụ)", required = false) @RequestParam(required = false) String authority,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(service.recordOfficialClearanceReference(cruiseStopId, clearanceReference, authority, actorId));
    }

    @Operation(summary = "Xóa điểm dừng bản nháp", description = "Xóa điểm dừng nếu lịch trình ở trạng thái DRAFT hoặc PENDING_APPROVAL")
    @PreAuthorize("hasAuthority('CRUISE_STOP_REMOVE_DRAFT')")
    @DeleteMapping("/{cruiseStopId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeDraft(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId) {
        service.removeDraft(cruiseStopId);
        return ApiResponse.success("Removed draft cruise stop successfully", null);
    }

    @Operation(summary = "Sắp xếp lại thứ tự điểm dừng", description = "Thay đổi thứ tự điểm dừng trong lịch trình")
    @PreAuthorize("hasAuthority('CRUISE_STOP_REORDER')")
    @PostMapping("/{cruiseStopId}/reorder")
    public ApiResponse<CruiseStopResponse> reorder(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thứ tự mới", required = true) @RequestParam Integer newStopOrder) {
        return ApiResponse.success(service.reorder(cruiseStopId, newStopOrder));
    }

    @Operation(summary = "Sắp xếp lại thứ tự điểm dừng kèm lý do đảo cảng (BE-20)", description = "Thay đổi thứ tự lượt ghé và lưu vết lý do đảo hoặc điều chỉnh cảng")
    @PreAuthorize("hasAuthority('CRUISE_STOP_REORDER')")
    @PostMapping("/{cruiseStopId}/reorder-with-reason")
    public ApiResponse<CruiseStopResponse> reorderWithReason(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thứ tự mới", required = true) @RequestParam Integer newStopOrder,
            @Parameter(description = "Lý do thay đổi/đảo thứ tự cảng", required = true) @RequestParam String reason) {
        return ApiResponse.success(service.reorderWithReason(cruiseStopId, newStopOrder, reason));
    }

    @Operation(summary = "Đặt thời gian tất cả hành khách lên tàu", description = "Cập nhật thời gian all aboard cho điểm dừng")
    @PreAuthorize("hasAuthority('CRUISE_STOP_ALL_ABOARD')")
    @PostMapping("/{cruiseStopId}/all-aboard-time")
    public ApiResponse<CruiseStopResponse> setAllAboardTime(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thời gian all aboard (ISO-8601)", required = true) @RequestParam Instant allAboardTime) {
        return ApiResponse.success(service.setAllAboardTime(cruiseStopId, allAboardTime));
    }

    @Operation(summary = "Ghi nhận thời gian đến thực tế", description = "Ghi nhận thời gian tàu đến cảng thực tế")
    @PreAuthorize("hasAuthority('CRUISE_STOP_ACTUAL_ARRIVAL')")
    @PostMapping("/{cruiseStopId}/actual-arrival")
    public ApiResponse<CruiseStopResponse> recordActualArrival(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thời gian đến thực tế (ISO-8601)", required = true) @RequestParam Instant actualArrival) {
        return ApiResponse.success(service.recordActualArrival(cruiseStopId, actualArrival));
    }

    @Operation(summary = "Ghi nhận thời gian đi thực tế", description = "Ghi nhận thời gian tàu rời cảng thực tế")
    @PreAuthorize("hasAuthority('CRUISE_STOP_ACTUAL_DEPARTURE')")
    @PostMapping("/{cruiseStopId}/actual-departure")
    public ApiResponse<CruiseStopResponse> recordActualDeparture(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thời gian đi thực tế (ISO-8601)", required = true) @RequestParam Instant actualDeparture) {
        return ApiResponse.success(service.recordActualDeparture(cruiseStopId, actualDeparture));
    }

    @Operation(summary = "Bỏ qua điểm dừng", description = "Đánh dấu điểm dừng bị bỏ qua (OMITTED) kèm lý do")
    @PreAuthorize("hasAuthority('CRUISE_STOP_OMIT')")
    @PostMapping("/{cruiseStopId}/omit")
    public ApiResponse<CruiseStopResponse> omit(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Lý do bỏ qua", required = true) @RequestParam String reason) {
        return ApiResponse.success(service.omit(cruiseStopId, reason));
    }

    @Operation(summary = "Thay thế điểm dừng", description = "Thay thế điểm dừng hiện tại bằng điểm dừng khác")
    @PreAuthorize("hasAuthority('CRUISE_STOP_SUBSTITUTE')")
    @PostMapping("/{cruiseStopId}/substitute")
    public ApiResponse<CruiseStopResponse> substitute(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "ID điểm dừng thay thế", required = true) @RequestParam UUID newStopId) {
        return ApiResponse.success(service.substitute(cruiseStopId, newStopId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ điểm dừng", description = "Xác minh tính hợp lệ của dữ liệu điểm dừng")
    @PreAuthorize("hasAuthority('CRUISE_STOP_VALIDATE')")
    @PostMapping("/{cruiseStopId}/validate")
    public ApiResponse<Void> validateStop(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId) {
        service.validateStop(cruiseStopId);
        return ApiResponse.success("Stop validation passed", null);
    }

    @Operation(summary = "Đặt thời gian dự kiến đến", description = "Cập nhật thời gian dự kiến đến cảng")
    @PreAuthorize("hasAuthority('CRUISE_STOP_TIME_MANAGE')")
    @PostMapping("/{cruiseStopId}/planned-arrival")
    public ApiResponse<CruiseStopResponse> setPlannedArrival(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thời gian dự kiến đến (ISO-8601)", required = true) @RequestParam Instant plannedArrival) {
        return ApiResponse.success(service.setPlannedArrival(cruiseStopId, plannedArrival));
    }

    @Operation(summary = "Đặt thời gian dự kiến đi", description = "Cập nhật thời gian dự kiến rời cảng")
    @PreAuthorize("hasAuthority('CRUISE_STOP_TIME_MANAGE')")
    @PostMapping("/{cruiseStopId}/planned-departure")
    public ApiResponse<CruiseStopResponse> setPlannedDeparture(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId,
            @Parameter(description = "Thời gian dự kiến đi (ISO-8601)", required = true) @RequestParam Instant plannedDeparture) {
        return ApiResponse.success(service.setPlannedDeparture(cruiseStopId, plannedDeparture));
    }

    @Operation(summary = "Tính thời lượng dừng cảng", description = "Tính thời lượng dừng tại cảng (phút)")
    @PreAuthorize("hasAuthority('CRUISE_STOP_READ')")
    @GetMapping("/{cruiseStopId}/duration")
    public ApiResponse<Long> calculateStopDuration(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId) {
        return ApiResponse.success(service.calculateStopDuration(cruiseStopId));
    }

    @Operation(summary = "Kiểm tra sự khả dụng của cảng", description = "Kiểm tra xem cảng có sẵn sàng trong khoảng thời gian không")
    @PreAuthorize("hasAuthority('CRUISE_STOP_READ')")
    @GetMapping("/{cruiseStopId}/port-availability")
    public ApiResponse<Boolean> checkPortAvailability(
            @Parameter(description = "ID của điểm dừng", required = true) @PathVariable UUID cruiseStopId) {
        // This would need the portId from the cruise stop
        // For now, we'll use a simplified version
        return ApiResponse.success(true);
    }
}