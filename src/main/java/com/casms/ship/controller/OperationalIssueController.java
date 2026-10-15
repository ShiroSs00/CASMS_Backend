package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.OperationalIssueAssignRequest;
import com.casms.ship.dto.request.OperationalIssueRequest;
import com.casms.ship.dto.request.OperationalIssueResolutionRequest;
import com.casms.ship.dto.request.OperationalIssueUpdateRequest;
import com.casms.ship.dto.response.OperationalIssueResponse;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.service.OperationalIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Operational Issue Management", description = "Quản lý, báo cáo và giải quyết sự cố vận hành trên tàu/chuyến đi")
@RestController
@RequestMapping("/operational-issues")
@RequiredArgsConstructor
public class OperationalIssueController {

    private final OperationalIssueService issueService;

    @Operation(summary = "Báo cáo sự cố mới", description = "Tạo báo cáo sự cố vận hành mới phát sinh trên tàu hoặc chuyến đi")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> report(
            @Valid @RequestBody OperationalIssueRequest request,
            @RequestParam(required = false) UUID reporterId,
            @AuthenticationPrincipal CasmsPrincipal principal) {

        UUID effectiveReporterId = (principal != null && principal.getUserId() != null)
                ? principal.getUserId()
                : reporterId;

        if (effectiveReporterId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "reporterId is required");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(issueService.report(request, effectiveReporterId)));
    }

    @Operation(summary = "Danh sách sự cố", description = "Lấy danh sách các sự cố vận hành theo bộ lọc (trạng thái, tàu, chuyến đi)")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationalIssueResponse>>> list(
            @RequestParam(required = false) OperationalIssueStatus status,
            @RequestParam(required = false) UUID shipId,
            @RequestParam(required = false) UUID cruiseId) {
        return ResponseEntity.ok(ApiResponse.success(
                issueService.list(status, shipId, cruiseId)));
    }

    @Operation(summary = "Chi tiết sự cố", description = "Lấy thông tin chi tiết của một sự cố vận hành theo ID")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_READ')")
    @GetMapping("/{issueId}")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> detail(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(ApiResponse.success(issueService.detail(issueId)));
    }

    @Operation(summary = "Cập nhật sự cố đang mở", description = "Cập nhật thông tin mô tả, mức độ nghiêm trọng khi sự cố còn ở trạng thái mở")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_UPDATE')")
    @PutMapping("/{issueId}")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> update(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                issueService.updateOpen(issueId, request)));
    }

    @Operation(summary = "Gán người xử lý sự cố", description = "Phân công nhân sự chịu trách nhiệm xử lý sự cố")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_ASSIGN')")
    @PatchMapping("/{issueId}/assign")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> assignHandler(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueAssignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                issueService.assignHandler(issueId, request)));
    }

    @Operation(summary = "Bắt đầu xử lý sự cố", description = "Chuyển trạng thái sự cố sang đang tiến hành xử lý (IN_PROGRESS)")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_UPDATE')")
    @PatchMapping("/{issueId}/start")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> startHandling(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(ApiResponse.success(
                issueService.startHandling(issueId)));
    }

    @Operation(summary = "Ghi nhận giải pháp xử lý", description = "Ghi nhận giải pháp và kết quả khắc phục sự cố")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_RESOLVE')")
    @PostMapping("/{issueId}/resolution")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> recordResolution(
            @PathVariable UUID issueId,
            @RequestParam(required = false) UUID resolverId,
            @AuthenticationPrincipal CasmsPrincipal principal,
            @Valid @RequestBody OperationalIssueResolutionRequest request) {

        UUID actorId = resolveActorId(principal, resolverId);
        return ResponseEntity.ok(ApiResponse.success(
                issueService.recordResolution(issueId, actorId, request)));
    }

    @Operation(summary = "Xác minh giải pháp khắc phục", description = "Xác minh và nghiệm thu kết quả xử lý sự cố")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_RESOLVE')")
    @PatchMapping("/{issueId}/verify")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> verifyResolution(
            @PathVariable UUID issueId,
            @RequestParam(required = false) UUID verifierId,
            @AuthenticationPrincipal CasmsPrincipal principal) {

        UUID actorId = resolveActorId(principal, verifierId);
        return ResponseEntity.ok(ApiResponse.success(
                issueService.verifyResolution(issueId, actorId)));
    }

    @Operation(summary = "Đóng sự cố", description = "Đóng sự cố sau khi đã hoàn tất xử lý và xác minh")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_RESOLVE')")
    @PatchMapping("/{issueId}/close")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> close(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(ApiResponse.success(issueService.close(issueId)));
    }

    @Operation(summary = "Mở lại sự cố", description = "Mở lại sự cố nếu phát sinh vấn đề hoặc tái diễn")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_UPDATE')")
    @PatchMapping("/{issueId}/reopen")
    public ResponseEntity<ApiResponse<OperationalIssueResponse>> reopen(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(ApiResponse.success(issueService.reopen(issueId)));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ sự cố", description = "Kiểm tra tính hợp lệ của dữ liệu sự cố")
    @PreAuthorize("hasAuthority('OPERATIONAL_ISSUE_READ')")
    @PostMapping("/{issueId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID issueId) {
        issueService.validateIssue(issueId);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveActorId(CasmsPrincipal principal, UUID clientActorId) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication required");
        }
        UUID principalId = principal.getUserId();
        if (clientActorId != null && !clientActorId.equals(principalId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Cannot act on behalf of another user");
        }
        return principalId;
    }
}
