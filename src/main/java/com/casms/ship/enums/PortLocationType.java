package com.casms.ship.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Loại vị trí cảng/neo đậu")
public enum PortLocationType {
    PORT,
    PIER,
    ANCHORAGE
}
