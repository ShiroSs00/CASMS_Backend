package com.casms.trip.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phân loại mục kế hoạch trong lịch trình (TripPlanEntry)")
public enum TripPlanEntryKind {
    ONBOARD_ACTIVITY,
    SHORE_EXCURSION,
    SERVING_WINDOW
}
