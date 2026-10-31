package com.casms.ship.dto.request;

import com.casms.ship.enums.ShipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Yêu cầu tạo mới tàu")
public class ShipCreateRequest {

    @Schema(description = "Tên tàu; chỉ bổ sung khi mô hình không có tên", example = "Ocean Voyager")
    @Size(max=100)
    private String shipName;

    @Schema(description = "Trạng thái hoạt động ban đầu (mặc định DRAFT)", example = "DRAFT")
    private ShipStatus status;

    @Schema(description = "Tổng sức chứa; chỉ bổ sung khi mô hình không có", example = "2500")
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @Schema(description = "Số boong hành khách; lấy từ mô hình nếu có", example = "12")
    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    @Positive
    private Double lengthMeters;

    @Positive
    private Double beamMeters;

    @Schema(description = "Ngày đưa vào hoạt động / hạ thủy", example = "2024-01-15")
    @PastOrPresent
    private LocalDate commissionDate;

    @Schema(description="Mô hình đã tải lên để tạo tàu; dùng cùng assetVersion. Không tải lại file.")
    @Size(max=80) @Pattern(regexp="[a-z0-9-]+")
    private String modelKey;

    @Size(max=80) @Pattern(regexp="[a-zA-Z0-9._-]+")
    private String assetVersion;

    @AssertTrue(message="modelKey and assetVersion must be supplied together") @JsonIgnore
    public boolean isModelReferenceComplete() {return (modelKey==null)==(assetVersion==null);}

    @AssertTrue(message="Ship name, total capacity and total decks are required when no uploaded model is selected") @JsonIgnore
    public boolean isManualDetailsComplete() {
        return modelKey!=null || shipName!=null && !shipName.isBlank() && totalCapacity!=null && totalDecks!=null;
    }
}
