package com.casms.trip.dto.response;

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
@Schema(description = "Thông tin tầng / boong tàu (Deck) cho lập lịch trình hoạt động trên tàu")
public class PlanningSourceDeckResponse {

    @Schema(description = "ID tầng / boong tàu")
    private UUID deckId;

    @Schema(description = "Số tầng", example = "2")
    private Integer deckNumber;

    @Schema(description = "Tên tầng hiển thị", example = "Lotus Wellness & Spa Deck")
    private String deckName;

    @Schema(description = "ID tàu du lịch")
    private UUID shipId;

    @Schema(description = "Tên tàu", example = "OceVie Paradise of the Seas")
    private String shipName;
}
