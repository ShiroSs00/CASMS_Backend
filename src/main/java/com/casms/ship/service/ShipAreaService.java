package com.casms.ship.service;

import com.casms.ship.dto.response.ShipAreaResponse;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.enums.ShipModelObjectType;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipAreaService {
    private final ShipModelRepository models;
    private final ShipModelObjectRepository objects;
    private final ObjectMapper json;

    private Optional<ShipModel> selectedModel(UUID shipId) {
        var versions = models.findByShipShipIdOrderByCreatedAtDesc(shipId);
        return versions.stream().filter(m -> m.getStatus() == ShipModelStatus.READY).findFirst()
            .or(() -> versions.stream().filter(m -> m.getStatus() == ShipModelStatus.DRAFT).findFirst());
    }
    public long count(UUID shipId) {
        return selectedModel(shipId).map(m -> objects.countByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(m.getShipModelId(), ShipModelObjectType.FACILITY)).orElse(0L);
    }
    public List<ShipAreaResponse> list(UUID shipId) {
        return selectedModel(shipId).map(m -> objects.findByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(m.getShipModelId(), ShipModelObjectType.FACILITY)
            .stream().filter(o -> shipId.equals(o.getDeck().getShip().getShipId()))
            .map(o -> new ShipAreaResponse(shipId, o.getShipModelObjectId(), o.getDisplayName(), kind(o.getMetadataJson()), o.getDeck().getDeckId(), o.getDeck().getDeckName(), o.getDeck().getDeckNumber()))
            .sorted(Comparator.comparing(ShipAreaResponse::deckNumber).thenComparing(a -> Objects.toString(a.name(), "")))
            .toList()).orElseGet(List::of);
    }
    private String kind(String metadata) {
        if (metadata == null) return null;
        try { return json.readTree(metadata).path("kind").asText(null); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return null; }
    }
}
