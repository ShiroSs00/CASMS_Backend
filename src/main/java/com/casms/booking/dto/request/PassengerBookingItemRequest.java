package com.casms.booking.dto.request;

import com.casms.identity.enums.PassengerDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PassengerBookingItemRequest {

    @Schema(description = "Họ và tên hành khách", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Passenger full name is required")
    @Size(min = 2, max = 100, message = "Passenger full name must be between 2 and 100 characters")
    private String fullName;

    @Schema(description = "Ngày sinh", example = "1990-05-15")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Schema(description = "Quốc tịch (Mã ISO 2-3 ký tự)", example = "VNM")
    @Size(min = 2, max = 10, message = "Nationality code must be between 2 and 10 characters (e.g. VNM, USA)")
    private String nationality;

    @Schema(description = "Loại giấy tờ tùy thân", example = "Passport")
    private PassengerDocumentType documentType;

    @Schema(description = "Số giấy tờ tùy thân", example = "B1234567")
    @Size(max = 30, message = "Document number must not exceed 30 characters")
    private String documentNumber;

    @Schema(description = "Email liên hệ", example = "passenger@example.com")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(description = "Số điện thoại", example = "+84912345678")
    @Pattern(regexp = "^(\\+?[0-9]{8,15})?$", message = "Phone number must be valid (8 to 15 digits, optional + prefix)")
    private String phoneNumber;

    @Schema(description = "ID phòng hành khách tự chọn", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cabin ID is required for passenger self-selection")
    private UUID cabinId;

    @Schema(description = "ID gói dịch vụ đã chọn (VD: BASIC, PLUS, PREMIUM)", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID tourPackageId;
}
