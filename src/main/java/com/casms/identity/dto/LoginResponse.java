package com.casms.identity.dto;

import com.casms.identity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Schema(description = "Phản hồi đăng nhập thành công")
public class LoginResponse {

    @Schema(description = "Access token JWT", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @Schema(description = "Loại token", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tokenType = "Bearer";

    @Schema(description = "Thời gian hết hạn (giây)", example = "86400", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expiresIn;

    @Schema(description = "ID người dùng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "Tên đăng nhập", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "Email", example = "admin@casms.com")
    private String email;

    @Schema(description = "Danh sách vai trò", example = "[\"ADMIN\", \"CRUISE_MANAGER\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<Role> roles;
}