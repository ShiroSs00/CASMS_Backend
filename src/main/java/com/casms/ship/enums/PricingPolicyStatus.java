package com.casms.ship.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái chính sách giá của chuyến đi (BE-H01)")
public enum PricingPolicyStatus {
    DRAFT,
    PENDING_APPROVAL,
    RETURNED,
    APPROVED,
    ARCHIVED
}
