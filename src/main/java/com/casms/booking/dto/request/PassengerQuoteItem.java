package com.casms.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerQuoteItem {
    @NotNull(message = "Cabin ID is required")
    private UUID cabinId;
    private UUID tourPackageId;
}
