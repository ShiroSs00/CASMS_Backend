package com.casms.ship.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DeckUpdateRequest {
    @Min(value = 1, message = "Deck number must be at least 1")
    private Integer deckNumber;

    private String deckName;
}