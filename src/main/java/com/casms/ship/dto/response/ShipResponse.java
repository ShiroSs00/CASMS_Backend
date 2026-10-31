package com.casms.ship.dto.response;

import com.casms.ship.enums.ShipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết tàu")
public class ShipResponse {

    @Schema(description = "ID duy nhất của tàu (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Mã tàu do hệ thống cấp", example = "SH-0001")
    private String shipCode;

    @Schema(description = "Tên tàu", example = "Ocean Voyager")
    private String shipName;

    @Schema(description = "Trạng thái tàu", example = "READY")
    private ShipStatus status;

    @Schema(description = "Tổng sức chứa", example = "2500")
    private Integer totalCapacity;

    @Schema(description = "Tổng số boong", example = "12")
    private Integer totalDecks;

    private Integer deckNumberLimit;

    private String manifestModelId;

    private Double lengthMeters;

    private Double beamMeters;

    @Schema(description = "Ngày hạ thủy", example = "2024-01-15")
    private LocalDate commissionDate;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
