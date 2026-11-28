package com.casms.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class BookingContractAcceptRequest {

    @Schema(
            description = "Mã tài liệu / số hiệu hồ sơ bản cứng đã ký (bắt buộc với nhân viên quầy; khách hàng xác nhận hợp đồng điện tử có thể để trống)",
            example = "DOC-CTR-BK0001-20261023")
    @jakarta.validation.constraints.Size(max = 255)
    private String documentReference;

    @Schema(
            description =
                    "ID hành khách đại diện ký hợp đồng (nếu không truyền, tự động lấy hành khách"
                        + " đại diện của booking)",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID acceptedByBookingPassengerId;

    @Schema(description = "Thời điểm ký kết thực tế (nếu không truyền, lấy thời điểm hiện tại)")
    @jakarta.validation.constraints.PastOrPresent
    private Instant acceptedAt;

    @Schema(description = "Không tiếp nhận nội dung hoặc ghi chú hợp đồng; trường này phải để null")
    @jakarta.validation.constraints.Null(message = "Contract notes and contents must not be stored; provide references only")
    private String notes;
}
