package com.casms.trip.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái tổng thể của Trip theo thiết kế mới (DRAFT, READY, ARCHIVED)")
public enum TripStatus {
    DRAFT,
    READY,
    ARCHIVED
}
