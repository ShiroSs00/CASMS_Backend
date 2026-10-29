package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruisePreparationConfirmRequest;
import com.casms.ship.dto.request.CruisePreparationReturnRequest;
import com.casms.ship.dto.request.CruisePreparationSubmitRequest;
import com.casms.ship.dto.response.CruisePreparationResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse;
import com.casms.ship.service.CruisePreparationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/cruises/{cruiseId}/preparation", "/cruise/{cruiseId}/preparation", "/api/cruises/{cruiseId}/preparation", "/api/cruise/{cruiseId}/preparation"})
@RequiredArgsConstructor
@Tag(name = "Cruise Preparation", description = "Quản lý chuẩn bị liên phòng ban và ký duyệt sẵn sàng (BE-07 / Mainflow S3)")
public class CruisePreparationController {

    private final CruisePreparationService preparationService;

    public record CoordinatorRequest(@jakarta.validation.constraints.NotNull UUID coordinatorUserId,@jakarta.validation.constraints.NotNull Long expectedRevision){}
    @PutMapping("/coordinator")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STAFF_ASSIGN')")
    public ApiResponse<CruisePreparationResponse> assignCoordinator(@PathVariable UUID cruiseId,@Valid @RequestBody CoordinatorRequest request,@AuthenticationPrincipal CasmsPrincipal principal){
        return ApiResponse.success(preparationService.assignCoordinator(cruiseId,request.coordinatorUserId(),request.expectedRevision(),principal.getUserId()));
    }
    @GetMapping("/coordinator-candidates")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_STAFF_ASSIGN')")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> coordinatorCandidates(@PathVariable UUID cruiseId,@AuthenticationPrincipal CasmsPrincipal principal){
        return ApiResponse.success(preparationService.coordinatorCandidates(cruiseId,principal));
    }
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('CRUISE_PREPARATION_READ')")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> events(@PathVariable UUID cruiseId,@AuthenticationPrincipal CasmsPrincipal principal){
        return ApiResponse.success(preparationService.events(cruiseId,principal));
    }

    @Operation(summary = "Xem báo cáo chuẩn bị liên phòng ban", description = "Truy xuất danh sách các phân hệ chuẩn bị, trạng thái công việc và bằng chứng")
    @PreAuthorize("hasAuthority('CRUISE_PREPARATION_READ')")
    @GetMapping
    public ApiResponse<CruisePreparationResponse> getPreparation(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(preparationService.getPreparationDashboard(cruiseId, principal));
    }

    @Operation(summary = "Gửi hồ sơ chuẩn bị", description = "CRM tổng hợp và gửi cả năm nhóm; ownerRole giữ trách nhiệm dữ liệu chuyên môn")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_PREPARATION_SUBMIT')")
    @PostMapping("/{sectionCode}/submit")
    public ApiResponse<CruisePreparationSectionResponse> submitSection(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Mã phân hệ chuẩn bị", required = true) @PathVariable String sectionCode,
            @RequestBody @Valid CruisePreparationSubmitRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        Set<String> roles = (principal != null && principal.getUser() != null && principal.getUser().getRoles() != null)
                ? principal.getUser().getRoles().stream().map(Enum::name).collect(Collectors.toSet())
                : Set.of();
        return ApiResponse.success("Nộp bằng chứng chuẩn bị thành công",
                preparationService.submitSection(cruiseId, sectionCode, request, actorId, roles));
    }

    @Operation(summary = "Phê duyệt ký nhận phân hệ chuẩn bị", description = "Ban Vận hành xác nhận ký nhận (Sign-off) đạt yêu cầu cho phân hệ")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PREPARATION_CONFIRM')")
    @PostMapping("/{sectionCode}/confirm")
    public ApiResponse<CruisePreparationSectionResponse> confirmSection(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Mã phân hệ chuẩn bị", required = true) @PathVariable String sectionCode,
            @RequestBody(required = false) CruisePreparationConfirmRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        String username = principal != null ? principal.getUsername() : "SYSTEM";
        return ApiResponse.success("Phê duyệt ký nhận phân hệ thành công",
                preparationService.confirmSection(cruiseId, sectionCode, request, actorId, username));
    }

    @Operation(summary = "Trả lại yêu cầu bổ sung bằng chứng", description = "Ban Vận hành trả lại phân hệ kèm lý do yêu cầu bộ phận liên quan bổ sung")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PREPARATION_CONFIRM')")
    @PostMapping("/{sectionCode}/return")
    public ApiResponse<CruisePreparationSectionResponse> returnSection(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @Parameter(description = "Mã phân hệ chuẩn bị", required = true) @PathVariable String sectionCode,
            @RequestBody @Valid CruisePreparationReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Đã trả lại phân hệ yêu cầu bổ sung",
                preparationService.returnSection(cruiseId, sectionCode, request, actorId));
    }
}
