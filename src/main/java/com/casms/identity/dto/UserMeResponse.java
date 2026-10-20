package com.casms.identity.dto;

import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Thông tin tài khoản và quyền hạn hiệu lực của phiên đăng nhập hiện tại")
public class UserMeResponse {

    @Schema(description = "ID người dùng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "Tên đăng nhập", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "Email người dùng", example = "admin@casms.com")
    private String email;

    @Schema(description = "Trạng thái tài khoản", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private UserStatus status;

    @Schema(description = "Danh sách vai trò", example = "[\"ADMIN\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<Role> roles;

    @Schema(description = "Danh sách quyền hiệu lực thực tế", example = "[\"CRUISE_READ\", \"ITINERARY_CREATE\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> effectivePermissions;
}
