package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ItineraryDayCreateRequest;
import com.casms.ship.dto.request.ItineraryDayUpdateRequest;
import com.casms.ship.dto.response.ItineraryDayResponse;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.service.ItineraryDayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Itinerary Day Management", description = "Quản lý ngày trong lịch trình chuyến đi")
@RestController
@RequestMapping("/itinerary-days")
@RequiredArgsConstructor
public class ItineraryDayController {

    private final ItineraryDayService service;

    @Operation(summary = "Tạo ngày mới trong lịch trình", description = "Thêm ngày mới vào lịch trình (chỉ khi lịch trình ở trạng thái DRAFT hoặc PENDING_APPROVAL)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ItineraryDayResponse> create(@RequestBody @Valid ItineraryDayCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "Danh sách ngày theo lịch trình", description = "Lấy danh sách tất cả ngày trong một lịch trình (đã sắp xếp theo thứ tự)")
    @GetMapping("/by-itinerary/{itineraryId}")
    public ApiResponse<List<ItineraryDayResponse>> listByItinerary(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        return ApiResponse.success(service.listByItinerary(itineraryId));
    }

    @Operation(summary = "Chi tiết ngày lịch trình", description = "Lấy thông tin chi tiết của một ngày trong lịch trình theo ID")
    @GetMapping("/{itineraryDayId}")
    public ApiResponse<ItineraryDayResponse> detail(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId) {
        return ApiResponse.success(service.detail(itineraryDayId));
    }

    @Operation(summary = "Cập nhật ngày lịch trình", description = "Cập nhật thông tin ngày (chỉ khi lịch trình ở trạng thái DRAFT hoặc PENDING_APPROVAL)")
    @PatchMapping("/{itineraryDayId}")
    public ApiResponse<ItineraryDayResponse> update(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId,
            @RequestBody @Valid ItineraryDayUpdateRequest request) {
        return ApiResponse.success(service.update(itineraryDayId, request));
    }

    @Operation(summary = "Xóa ngày chưa sử dụng", description = "Xóa ngày nếu không có điểm dừng nào và lịch trình ở trạng thái DRAFT/PENDING_APPROVAL")
    @DeleteMapping("/{itineraryDayId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId) {
        service.deleteUnused(itineraryDayId);
        return ApiResponse.success("Deleted unused itinerary day successfully", null);
    }

    @Operation(summary = "Đặt loại ngày", description = "Cập nhật loại ngày (SEA_DAY hoặc PORT_DAY)")
    @PostMapping("/{itineraryDayId}/day-type")
    public ApiResponse<ItineraryDayResponse> setDayType(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId,
            @Parameter(description = "Loại ngày mới", required = true) @RequestParam ItineraryDayType dayType) {
        return ApiResponse.success(service.setDayType(itineraryDayId, dayType));
    }

    @Operation(summary = "Đặt múi giờ tàu", description = "Cập nhật múi giờ tàu cho ngày này")
    @PostMapping("/{itineraryDayId}/ship-timezone")
    public ApiResponse<ItineraryDayResponse> setShipTimeZone(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId,
            @Parameter(description = "Múi giờ tàu mới (IANA)", required = true) @RequestParam String shipTimeZoneId) {
        return ApiResponse.success(service.setShipTimeZone(itineraryDayId, shipTimeZoneId));
    }

    @Operation(summary = "Kiểm tra tính tuần tự ngày", description = "Xác minh các ngày trong lịch trình được đánh số tuần tự từ 1")
    @PostMapping("/by-itinerary/{itineraryId}/validate-sequence")
    public ApiResponse<Void> validateSequence(
            @Parameter(description = "ID của lịch trình", required = true) @PathVariable UUID itineraryId) {
        service.validateSequence(itineraryId);
        return ApiResponse.success("Day sequence validation passed", null);
    }

    @Operation(summary = "Kiểm tra tính hợp lệ ngày", description = "Xác minh tính hợp lệ của dữ liệu ngày")
    @PostMapping("/{itineraryDayId}/validate")
    public ApiResponse<Void> validateDay(
            @Parameter(description = "ID của ngày lịch trình", required = true) @PathVariable UUID itineraryDayId) {
        service.validateDay(itineraryDayId);
        return ApiResponse.success("Day validation passed", null);
    }
}