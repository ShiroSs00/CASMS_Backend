package com.casms.ship.controller;

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
    @PostMapping
    public ResponseEntity<OperationalIssueResponse> report(
            @Valid @RequestBody OperationalIssueRequest request,
            @RequestParam UUID reporterId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issueService.report(request, reporterId));
    }

    @Operation(summary = "Danh sách sự cố", description = "Lấy danh sách các sự cố vận hành theo bộ lọc (trạng thái, tàu, chuyến đi)")
    @GetMapping
    public ResponseEntity<List<OperationalIssueResponse>> list(
            @RequestParam(required = false) OperationalIssueStatus status,
            @RequestParam(required = false) UUID shipId,
            @RequestParam(required = false) UUID cruiseId) {
        return ResponseEntity.ok(
                issueService.list(status, shipId, cruiseId));
    }

    @Operation(summary = "Chi tiết sự cố", description = "Lấy thông tin chi tiết của một sự cố vận hành theo ID")
    @GetMapping("/{issueId}")
    public ResponseEntity<OperationalIssueResponse> detail(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.detail(issueId));
    }

    @Operation(summary = "Cập nhật sự cố đang mở", description = "Cập nhật thông tin mô tả, mức độ nghiêm trọng khi sự cố còn ở trạng thái mở")
    @PutMapping("/{issueId}")
    public ResponseEntity<OperationalIssueResponse> update(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueUpdateRequest request) {
        return ResponseEntity.ok(
                issueService.updateOpen(issueId, request));
    }

    @Operation(summary = "Gán người xử lý sự cố", description = "Phân công nhân sự chịu trách nhiệm xử lý sự cố")
    @PatchMapping("/{issueId}/assign")
    public ResponseEntity<OperationalIssueResponse> assignHandler(
            @PathVariable UUID issueId,
            @Valid @RequestBody OperationalIssueAssignRequest request) {
        return ResponseEntity.ok(
                issueService.assignHandler(issueId, request));
    }

    @Operation(summary = "Bắt đầu xử lý sự cố", description = "Chuyển trạng thái sự cố sang đang tiến hành xử lý (IN_PROGRESS)")
    @PatchMapping("/{issueId}/start")
    public ResponseEntity<OperationalIssueResponse> startHandling(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(
                issueService.startHandling(issueId));
    }

    @Operation(summary = "Ghi nhận giải pháp xử lý", description = "Ghi nhận giải pháp và kết quả khắc phục sự cố")
    @PostMapping("/{issueId}/resolution")
    public ResponseEntity<OperationalIssueResponse> recordResolution(
            @PathVariable UUID issueId,
            @RequestParam UUID resolverId,
            @Valid @RequestBody OperationalIssueResolutionRequest request) {
        return ResponseEntity.ok(
                issueService.recordResolution(issueId, resolverId, request));
    }

    @Operation(summary = "Xác minh giải pháp khắc phục", description = "Xác minh và nghiệm thu kết quả xử lý sự cố")
    @PatchMapping("/{issueId}/verify")
    public ResponseEntity<OperationalIssueResponse> verifyResolution(
            @PathVariable UUID issueId,
            @RequestParam UUID verifierId) {
        return ResponseEntity.ok(
                issueService.verifyResolution(issueId, verifierId));
    }

    @Operation(summary = "Đóng sự cố", description = "Đóng sự cố sau khi đã hoàn tất xử lý và xác minh")
    @PatchMapping("/{issueId}/close")
    public ResponseEntity<OperationalIssueResponse> close(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.close(issueId));
    }

    @Operation(summary = "Mở lại sự cố", description = "Mở lại sự cố nếu phát sinh vấn đề hoặc tái diễn")
    @PatchMapping("/{issueId}/reopen")
    public ResponseEntity<OperationalIssueResponse> reopen(
            @PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.reopen(issueId));
    }

    @Operation(summary = "Kiểm tra tính hợp lệ sự cố", description = "Kiểm tra tính hợp lệ của dữ liệu sự cố")
    @PostMapping("/{issueId}/validate")
    public ResponseEntity<Void> validate(
            @PathVariable UUID issueId) {
        issueService.validateIssue(issueId);
        return ResponseEntity.noContent().build();
    }
}
