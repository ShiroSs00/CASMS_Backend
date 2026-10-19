package com.casms.booking.dto.response;

import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AvailableCabinResponse {

    @Schema(description = "ID của phòng", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID cabinId;

    @Schema(description = "Số phòng", example = "B-302")
    private String cabinNumber;

    @Schema(description = "ID tầng/boong", example = "d1e2f3a4-b5c6-7890-abcd-ef1234567890")
    private UUID deckId;

    @Schema(description = "Số thứ tự tầng", example = "5")
    private Integer deckNumber;

    @Schema(description = "Tên tầng", example = "Promenade Deck")
    private String deckName;

    @Schema(description = "Loại phòng (INTERIOR, OCEAN_VIEW, BALCONY, SUITE)", example = "BALCONY")
    private CabinType cabinType;

    @Schema(description = "Tổng sức chứa của phòng", example = "2")
    private Integer capacity;

    @Schema(description = "Số chỗ còn trống trong phòng cho chuyến đi này", example = "2")
    private Integer remainingCapacity;

    @Schema(description = "Phòng có hỗ trợ người khuyết tật không", example = "false")
    private Boolean isAccessible;
}
