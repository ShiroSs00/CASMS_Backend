package com.casms.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.ToString;

import java.nio.charset.StandardCharsets;

@Data
@Schema(description = "Yêu cầu đăng nhập")
public class LoginRequest {

    @Schema(
            description = "Tên đăng nhập",
            example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username is required")
    private String username;

    @Schema(
            description = "Mật khẩu",
            example = "password123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    @ToString.Exclude
    private String password;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Username must be at most 100 characters")
    public boolean isUsernameWithinStorageLimit() {
        return username == null
                || (username.length() <= 200 && username.codePointCount(0, username.length()) <= 100);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
                || (password.length() <= 72 && password.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
