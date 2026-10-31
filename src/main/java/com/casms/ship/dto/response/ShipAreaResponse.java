package com.casms.ship.dto.response;
import java.util.UUID;
public record ShipAreaResponse(UUID shipId, UUID id, String name, String kind, UUID deckId, String deckName, Integer deckNumber) {}
