package com.casms.ship.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class DeckCreateRequest {
    @NotNull(message = "Ship ID is required")
    private UUID shipId;

    @NotNull(message = "Deck number is required")
    @Min(value = 1, message = "Deck number must be at least 1")
    private Integer deckNumber;

    private String deckName;
}