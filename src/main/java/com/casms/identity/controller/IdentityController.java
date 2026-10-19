package com.casms.identity.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.dto.UserLookupResponse;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Identity & Users", description = "Quản lý tài khoản, người dùng và hành khách")
@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService service;

    @Operation(summary = "Danh sách người dùng cho gán việc", description = "Lấy danh sách người dùng khả dụng để phân công xử lý sự cố hoặc tác vụ")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'OPERATIONAL_ISSUE_ASSIGN', 'OPERATIONAL_ISSUE_CREATE', 'OPERATIONAL_ISSUE_UPDATE')")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserLookupResponse>>> listUsers(
            @RequestParam(required = false, defaultValue = "ACTIVE") UserStatus status,
            @RequestParam(required = false) String assignableFor) {
        return ResponseEntity.ok(ApiResponse.success(service.findUsers(status, assignableFor)));
    }
}
