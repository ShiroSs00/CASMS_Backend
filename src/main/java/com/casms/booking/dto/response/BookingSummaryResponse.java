package com.casms.booking.dto.response;

import com.casms.booking.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class BookingSummaryResponse {

    @Schema(description = "ID của đơn đặt chỗ")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đặt chỗ", example = "BK-20261019-9B3E")
    private String bookingReference;

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi", example = "CRUISE-2026-001")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi", example = "Hành trình Vịnh Hạ Long 3N2Đ")
    private String cruiseName;

    @Schema(description = "Trạng thái đặt chỗ", example = "Draft")
    private BookingStatus bookingStatus;

    @Schema(description = "Tổng giá vé", example = "1200.0000")
    private BigDecimal agreedFareAmount;

    @Schema(description = "Tiền tệ", example = "USD")
    private String currency;

    @Schema(description = "Số lượng hành khách", example = "2")
    private Integer passengerCount;

    @Schema(description = "Thời điểm khởi hành chuyến đi")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm tạo đặt chỗ")
    private Instant createdAt;
}
