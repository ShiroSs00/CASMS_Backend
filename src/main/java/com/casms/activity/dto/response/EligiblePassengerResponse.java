package com.casms.activity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin hành khách đủ điều kiện phân bổ vào nhóm tour")
public record EligiblePassengerResponse(
        @Schema(description = "ID hành khách", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID passengerId,

        @Schema(description = "Họ và tên hành khách", example = "Nguyễn Văn A")
        String fullName,

        @Schema(description = "Email hành khách", example = "passenger@example.com")
        String email,

        @Schema(description = "Số điện thoại liên hệ", example = "+84901234567")
        String phoneNumber,

        @Schema(description = "Quốc tịch", example = "Vietnam")
        String nationality,

        @Schema(description = "ID lượt đăng ký tour trên bờ", example = "4ba85f64-5717-4562-b3fc-2c963f66afb7")
        UUID registrationId,

        @Schema(description = "Số lượng người đăng ký", example = "2")
        Integer quantity,

        @Schema(description = "Thời gian đăng ký xác nhận")
        Instant registrationTime
) {}
