package com.casms.booking.dto.response;

import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.identity.enums.PassengerDocumentType;
import com.casms.servicepackage.enums.TourPackageType;
import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BookingPassengerResponse {

    @Schema(description = "ID bản ghi hành khách trong booking (BookingPassenger ID)")
    private UUID bookingPassengerId;

    @Schema(description = "ID hồ sơ hành khách gốc (Passenger ID)")
    private UUID passengerId;

    @Schema(description = "Họ và tên hành khách", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "Ngày sinh", example = "1990-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Quốc tịch", example = "VNM")
    private String nationality;

    @Schema(description = "Loại giấy tờ tùy thân", example = "Passport")
    private PassengerDocumentType documentType;

    @Schema(description = "Số giấy tờ tùy thân", example = "B1234567")
    private String documentNumber;

    @Schema(description = "Email liên hệ", example = "passenger@example.com")
    private String email;

    @Schema(description = "Số điện thoại", example = "+84912345678")
    private String phoneNumber;

    @Schema(description = "Trạng thái tham gia (Active, Cancelled)", example = "Active")
    private BookingPassengerParticipationStatus participationStatus;

    @Schema(description = "Hạng phòng khách đã yêu cầu/chọn", example = "BALCONY")
    private String requestedCabinCategory;

    // Cabin Assignment Info (Khách tự chọn phòng)
    @Schema(description = "ID phòng đã được gán", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID cabinId;

    @Schema(description = "Số phòng", example = "B-302")
    private String cabinNumber;

    @Schema(description = "Loại phòng", example = "BALCONY")
    private CabinType cabinType;

    @Schema(description = "Tầng/boong", example = "5")
    private Integer deckNumber;

    // Tour Package Info
    @Schema(description = "ID gói dịch vụ đã chọn", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID tourPackageId;

    @Schema(description = "Tên gói dịch vụ", example = "Gói Thưởng Ngoạn Cao Cấp")
    private String tourPackageName;

    @Schema(description = "Loại gói dịch vụ (BASIC, PREMIUM, ADVANCED)", example = "PREMIUM")
    private TourPackageType tourPackageType;

    @Schema(description = "Giá gói dịch vụ đã chốt", example = "250.0000")
    private BigDecimal agreedPackagePrice;
}
