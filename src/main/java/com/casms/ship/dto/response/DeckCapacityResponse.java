package com.casms.ship.dto.response;

import lombok.Data;
import java.util.UUID;

@Data
public class DeckCapacityResponse {
    private UUID deckId;
    private String deckName;
    private Integer deckNumber;
    private Long totalCabins;
    private Long availableCabins;
    private Long totalPassengerCapacity;
}