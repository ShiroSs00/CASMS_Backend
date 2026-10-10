package com.casms.ship.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class DeckResponse {
    private UUID deckId;
    private UUID shipId;
    private Integer deckNumber;
    private String deckName;
    private Instant createdAt;
    private Instant updatedAt;
}