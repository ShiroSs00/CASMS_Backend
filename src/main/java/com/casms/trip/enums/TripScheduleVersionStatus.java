package com.casms.trip.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái phiên bản lịch trình mẫu của Trip")
public enum TripScheduleVersionStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED
}
