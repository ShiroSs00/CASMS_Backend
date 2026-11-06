package com.casms.ship.dto.request;

import com.casms.ship.enums.ShipStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Yêu cầu tạo mới tàu")
public class ShipCreateRequest {

    @Schema(
            description = "Tên tàu; chỉ bổ sung khi mô hình không có tên",
            example = "Ocean Voyager")
    @Size(max = 100)
    private String shipName;

    @Schema(description = "Trạng thái hoạt động ban đầu (mặc định DRAFT)", example = "DRAFT")
    private ShipStatus status;

    @Schema(description = "Tổng sức chứa; chỉ bổ sung khi mô hình không có", example = "2500")
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @Schema(description = "Số boong hành khách; lấy từ mô hình nếu có", example = "12")
    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    @Positive private Double lengthMeters;

    @Positive private Double beamMeters;

    @Schema(description = "Ngày đưa vào hoạt động / hạ thủy", example = "2024-01-15")
    @PastOrPresent
    private LocalDate commissionDate;

    @Schema(description = "Nhà máy hoặc đơn vị đóng tàu")
    @Size(max = 150)
    private String manufacturerName;

    @Schema(description = "Nơi xuất xứ của tàu")
    @Size(max = 100)
    private String origin;

    @Schema(description = "Năm đóng tàu")
    @Min(1)
    @Max(9999)
    private Integer productionYear;

    @Schema(description = "Loại động cơ")
    @Size(max = 100)
    private String engineType;

    @Schema(
            description =
                    "Mã mô hình đã tải lên. Hệ thống dùng gói hiện tại của mã này.")
    @Size(max = 80)
    @Pattern(regexp = "[a-z0-9-]+")
    private String modelKey;

    @AssertTrue(
            message =
                    "Ship name, total capacity and total decks are required when no uploaded model"
                        + " is selected")
    @JsonIgnore
    public boolean isManualDetailsComplete() {
        return modelKey != null
                || shipName != null
                        && !shipName.isBlank()
                        && totalCapacity != null
                        && totalDecks != null;
    }
}
