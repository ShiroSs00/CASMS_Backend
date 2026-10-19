package com.casms.identity.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.identity.dto.LoginRequest;
import com.casms.identity.dto.LoginResponse;
import com.casms.identity.dto.UserMeResponse;
import com.casms.identity.service.AuthService;
import com.casms.security.principal.CasmsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API xác thực và quản lý phiên đăng nhập")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng nhập", description = "Xác thực người dùng và trả về JWT access token kèm quyền hạn hiệu lực")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "Làm mới token", description = "Làm mới access token từ token hiện tại")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ApiResponse.success(authService.refreshToken(token));
    }

    @Operation(summary = "Thông tin phiên đăng nhập hiện tại", description = "Lấy thông tin người dùng, danh sách vai trò và quyền hạn hiệu lực (effectivePermissions)")
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal CasmsPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Yêu cầu đăng nhập để truy cập thông tin phiên");
        }
        return ApiResponse.success(authService.getCurrentUser(principal));
    }

    @Operation(summary = "Đăng xuất", description = "Đăng xuất (client-side xóa token)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // Với JWT stateless, logout chỉ cần client xóa token
        // Có thể implement blacklist token nếu cần bảo mật cao hơn
        return ApiResponse.success("Logged out successfully", null);
    }
}