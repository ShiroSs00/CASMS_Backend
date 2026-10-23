package com.casms.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class BookingContractAcceptRequest {

    @Schema(description = "Mã tài liệu / số hiệu hồ sơ bản cứng đã ký", example = "DOC-CTR-BK0001-20261023", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Document reference / physical filing code is required")
    private String documentReference;

    @Schema(description = "ID hành khách đại diện ký hợp đồng (nếu không truyền, tự động lấy hành khách đại diện của booking)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID acceptedByBookingPassengerId;

    @Schema(description = "Thời điểm ký kết thực tế (nếu không truyền, lấy thời điểm hiện tại)")
    private Instant acceptedAt;

    @Schema(description = "Ghi chú bổ sung / thông tin kiểm chứng biên bản bàn giao", example = "Đã ký tại quầy giao dịch OceVie Landmark 81")
    private String notes;
}
