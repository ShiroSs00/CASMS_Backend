package com.casms.booking.dto.request;

import com.casms.booking.enums.PaymentPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BookingCreateRequest {

    @Schema(description = "ID của chuyến đi muốn đặt", example = "11111111-2222-3333-4444-555555555555", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    @Schema(description = "Mã tiền tệ (3 ký tự)", example = "USD", defaultValue = "USD")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    @Schema(description = "Danh sách hành khách tham gia chuyến đi (tối thiểu 1 người)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "At least one passenger must be included in the booking")
    @Valid
    private List<PassengerBookingItemRequest> passengers;

    @Schema(description = "Hình thức thanh toán khách chọn: FULL (Toàn bộ 100%), DEPOSIT (Cọc 30%), STAGES (Trả theo 3 giai đoạn)", example = "DEPOSIT", defaultValue = "DEPOSIT")
    private PaymentPlan paymentPlan;

    @Schema(description = "ID của báo giá đã được cấp (nếu có, dùng để khóa giá và chính sách thanh toán)", example = "11111111-2222-3333-4444-555555555555")
    private UUID quoteId;

    @Schema(description = "Thông tin xác thực báo giá của khách chưa đăng nhập; chỉ dùng cho báo giá khách vãng lai")
    @Size(max = 100)
    private String quoteGuestToken;

    @Schema(description = "Ghi chú thêm về đơn đặt chỗ", example = "Yêu cầu bữa ăn chay nếu có")
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
