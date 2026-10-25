package com.casms.trip.dto.response;

import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin phòng / cabin (phòng chức năng, spa, nhà hàng, hội nghị) cho hoạt động trên tàu")
public class PlanningSourceCabinResponse {

    @Schema(description = "ID cabin / phòng")
    private UUID cabinId;

    @Schema(description = "Số phòng / mã vị trí", example = "202")
    private String cabinNumber;

    @Schema(description = "Tên phòng / tên công năng chuyên biệt", example = "Phòng Massage Đá Nóng VIP")
    private String roomName;

    @Schema(description = "Tên hiển thị trực quan cho khách (Số phòng + Tên công năng)", example = "Phòng 202 - Phòng Massage Đá Nóng VIP")
    private String displayName;

    @Schema(description = "Loại phòng / phân loại cabin", example = "SUITE")
    private CabinType cabinType;

    @Schema(description = "Sức chứa tối đa của phòng", example = "20")
    private Integer capacity;

    @Schema(description = "ID tầng chứa phòng này")
    private UUID deckId;

    @Schema(description = "Số tầng", example = "2")
    private Integer deckNumber;

    @Schema(description = "Tên tầng", example = "Lotus Wellness & Spa Deck")
    private String deckName;

    @Schema(description = "Vị trí tương đối trên tàu", example = "MIDSHIP-PORT")
    private String locationCode;

    @Schema(description = "ID tàu du lịch")
    private UUID shipId;
}
