package com.casms.checkin.controller;

import com.casms.checkin.dto.request.GuidanceAttendanceRequest;
import com.casms.checkin.dto.request.GuidanceSessionCreateRequest;
import com.casms.checkin.dto.response.GuidanceParticipantResponse;
import com.casms.checkin.dto.response.GuidanceSessionResponse;
import com.casms.checkin.enums.GuidanceSessionStatus;
import com.casms.checkin.service.GuidanceSessionService;
import com.casms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Guidance Sessions", description = "Quản lý các buổi phổ cập thông tin, hướng dẫn an toàn cứu sinh trước chuyến đi")
@RestController
@RequestMapping("/checkin/guidance-sessions")
@RequiredArgsConstructor
public class GuidanceSessionController {

    private final GuidanceSessionService guidanceSessionService;

    @Operation(summary = "Tạo buổi phổ cập thông tin trước chuyến", description = "Lên lịch buổi hướng dẫn an toàn, cứu sinh hoặc quy định chuyến đi cho hành khách")
    @PreAuthorize("hasAuthority('CHECKIN_CREATE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GuidanceSessionResponse> create(
            @RequestBody @Valid GuidanceSessionCreateRequest request) {
        return ApiResponse.success("Guidance session created successfully", guidanceSessionService.createSession(request));
    }

    @Operation(summary = "Danh sách các buổi phổ cập theo chuyến đi", description = "Lấy tất cả các buổi hướng dẫn được thiết lập cho một chuyến đi cụ thể")
    @PreAuthorize("permitAll()")
    @GetMapping("/cruise/{cruiseId}")
    public ApiResponse<List<GuidanceSessionResponse>> listByCruise(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(guidanceSessionService.listByCruise(cruiseId));
    }

    @Operation(summary = "Chi tiết buổi phổ cập thông tin", description = "Lấy thông tin chi tiết buổi hướng dẫn kèm số liệu điểm danh")
    @PreAuthorize("permitAll()")
    @GetMapping("/{sessionId}")
    public ApiResponse<GuidanceSessionResponse> getDetail(
            @Parameter(description = "ID buổi phổ cập", required = true) @PathVariable UUID sessionId) {
        return ApiResponse.success(guidanceSessionService.getDetail(sessionId));
    }

    @Operation(summary = "Cập nhật trạng thái buổi phổ cập", description = "Chuyển trạng thái Scheduled -> Completed hoặc Cancelled")
    @PreAuthorize("hasAuthority('CHECKIN_UPDATE')")
    @PatchMapping("/{sessionId}/status")
    public ApiResponse<GuidanceSessionResponse> updateStatus(
            @Parameter(description = "ID buổi phổ cập", required = true) @PathVariable UUID sessionId,
            @RequestParam GuidanceSessionStatus status) {
        return ApiResponse.success(guidanceSessionService.updateStatus(sessionId, status));
    }

    @Operation(summary = "Tự động thêm hành khách của chuyến vào buổi phổ cập", description = "Quét tất cả hành khách có booking active trên chuyến và ghi danh vào buổi phổ cập")
    @PreAuthorize("hasAuthority('CHECKIN_UPDATE')")
    @PostMapping("/{sessionId}/participants/auto-populate")
    public ApiResponse<Integer> autoPopulate(
            @Parameter(description = "ID buổi phổ cập", required = true) @PathVariable UUID sessionId) {
        int added = guidanceSessionService.autoPopulateParticipants(sessionId);
        return ApiResponse.success(String.format("Added %d participants to guidance session", added), added);
    }

    @Operation(summary = "Điểm danh hành khách tham gia buổi phổ cập", description = "Ghi nhận trạng thái tham dự (Registered, Attended, Absent) của hành khách")
    @PreAuthorize("hasAuthority('CHECKIN_UPDATE')")
    @PostMapping("/{sessionId}/attendance")
    public ApiResponse<GuidanceParticipantResponse> recordAttendance(
            @Parameter(description = "ID buổi phổ cập", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid GuidanceAttendanceRequest request) {
        return ApiResponse.success("Recorded attendance successfully", guidanceSessionService.recordAttendance(sessionId, request));
    }

    @Operation(summary = "Danh sách hành khách tham gia buổi phổ cập", description = "Lấy danh sách chi tiết các hành khách được ghi danh và trạng thái điểm danh")
    @PreAuthorize("hasAuthority('CHECKIN_UPDATE')")
    @GetMapping("/{sessionId}/participants")
    public ApiResponse<List<GuidanceParticipantResponse>> listParticipants(
            @Parameter(description = "ID buổi phổ cập", required = true) @PathVariable UUID sessionId) {
        return ApiResponse.success(guidanceSessionService.listParticipants(sessionId));
    }
}
