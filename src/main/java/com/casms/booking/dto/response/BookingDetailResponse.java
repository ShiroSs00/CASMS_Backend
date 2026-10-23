package com.casms.booking.dto.response;

import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BookingDetailResponse {

    @Schema(description = "ID của đơn đặt chỗ (Booking ID)")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đặt chỗ duy nhất", example = "BK-20261019-9B3E")
    private String bookingReference;

    @Schema(description = "Trạng thái đặt chỗ (Draft, Confirmed, Cancelled)", example = "Draft")
    private BookingStatus bookingStatus;

    // Thông tin chuyến đi
    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi", example = "CRUISE-2026-001")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi", example = "Hành trình Vịnh Hạ Long 3N2Đ")
    private String cruiseName;

    @Schema(description = "Tên tàu", example = "CASMS Serenade of the Seas")
    private String shipName;

    @Schema(description = "Thời gian khởi hành")
    private Instant departureDatetime;

    @Schema(description = "Thời gian kết thúc chuyến")
    private Instant returnDatetime;

    // Tài chính & Thanh toán
    @Schema(description = "Tổng giá vé đã chốt (Agreed Fare Amount)", example = "1200.0000")
    private BigDecimal agreedFareAmount;

    @Schema(description = "Mã tiền tệ", example = "USD")
    private String currency;

    @Schema(description = "Mức cọc yêu cầu tối thiểu", example = "360.0000")
    private BigDecimal requiredDepositAmount;

    @Schema(description = "Hạn thanh toán số tiền còn lại")
    private Instant finalPaymentDueAt;

    // Hợp đồng
    @Schema(description = "Mã hợp đồng đặt chỗ", example = "CTR-BK-20261019-9B3E")
    private String contractReference;

    @Schema(description = "Trạng thái hợp đồng (Draft, Accepted, Cancelled)", example = "Draft")
    private BookingContractStatus contractStatus;

    @Schema(description = "Mã tài liệu / hồ sơ lưu trữ hợp đồng bản cứng", example = "DOC-CTR-BK0001-20261023")
    private String contractDocumentReference;

    @Schema(description = "Thời điểm xác nhận chấp thuận / ký hợp đồng")
    private Instant contractAcceptedAt;

    // Người đặt & mốc thời gian
    @Schema(description = "ID người thực hiện đặt chỗ")
    private UUID bookedByUserId;

    @Schema(description = "Username người thực hiện đặt chỗ", example = "john_doe")
    private String bookedByUsername;

    @Schema(description = "Thời điểm tạo đặt chỗ")
    private Instant createdAt;

    @Schema(description = "Thời điểm xác nhận đặt chỗ")
    private Instant confirmedAt;

    @Schema(description = "Thời điểm hủy đặt chỗ")
    private Instant cancelledAt;

    @Schema(description = "Lý do hủy đặt chỗ")
    private String cancellationReason;

    @Schema(description = "Hình thức thanh toán khách đã chọn", example = "DEPOSIT")
    private com.casms.booking.enums.PaymentPlan paymentPlan;

    @Schema(description = "Lộ trình các đợt thanh toán theo hình thức đã chọn")
    private List<InstallmentResponse> installments;

    @Schema(description = "Hướng dẫn liên hệ và tới ký hợp đồng bản cứng bảo mật",
            example = "Vui lòng mang theo CCCD/Hộ chiếu gốc đến văn phòng đại diện tại Tầng 2, Tòa nhà CASMS Marina để ký kết hợp đồng dịch vụ trước ngày 2026-10-25.")
    private String contractSigningInstructions;

    // Danh sách hành khách & phòng tự chọn
    @Schema(description = "Danh sách chi tiết hành khách và phòng trong booking")
    private List<BookingPassengerResponse> passengers;
}
