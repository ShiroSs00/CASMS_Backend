package com.casms.ship.service;

import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicShipModelTest {
    final UUID shipId = UUID.randomUUID(), modelId = UUID.randomUUID();
    final ShipModelRepository models = mock(ShipModelRepository.class);
    final ShipModelObjectRepository objects = mock(ShipModelObjectRepository.class);
    final ShipModelAssetStore store = mock(ShipModelAssetStore.class);
    final ShipModelAssetService service = new ShipModelAssetService(null, models, objects, null, null, store, null, null);
    final Ship ship = new Ship();
    final ShipModel model = new ShipModel();

    @BeforeEach void setup() {
        ship.setShipId(shipId); ship.setStatus(ShipStatus.ACTIVE);
        model.setShip(ship); model.setShipModelId(modelId); model.setStatus(ShipModelStatus.READY);
        model.setModelKey("demo"); model.setAssetVersion("v1"); model.setAssetRevision("r1");
        when(models.findById(modelId)).thenReturn(Optional.of(model));
    }
    void manifest() throws Exception {
        var json = new ObjectMapper().readTree("""
            {"revision":"r1","assets":{"exterior":"exterior.glb"},"decks":[{"asset":"deck.glb"}],"cabins":[],"areas":[]}
            """);
        when(store.manifest("demo", "v1")).thenReturn(json);
        when(store.decks(json)).thenReturn(List.of(json.path("decks").get(0)));
    }
    @Test void publishedLayoutIncludesAreasAndMappings() throws Exception {
        manifest(); when(objects.findByShipModelShipModelId(modelId)).thenReturn(List.of());
        when(models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)).thenReturn(List.of(model));
        var result = service.publicModel(shipId);
        assertEquals(shipId, result.shipId()); assertEquals(modelId, result.modelId());
        assertTrue(result.manifest().has("areas")); assertTrue(result.cabinMappings().isEmpty());
    }
    @Test void draftArchivedWrongShipAndInactiveShipCannotExposeAssets() {
        for (var status : List.of(ShipModelStatus.DRAFT, ShipModelStatus.ARCHIVED)) {
            model.setStatus(status);
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.publicAsset(shipId, modelId, "exterior.glb")).getStatusCode().value());
        }
        model.setStatus(ShipModelStatus.READY);
        assertThrows(ResponseStatusException.class, () -> service.publicAsset(UUID.randomUUID(), modelId, "exterior.glb"));
        ship.setStatus(ShipStatus.INACTIVE);
        assertThrows(ResponseStatusException.class, () -> service.publicAsset(shipId, modelId, "exterior.glb"));
        verifyNoInteractions(store);
    }
    @Test void onlyManifestReferencedGlbsArePublic() throws Exception {
        manifest();
        service.publicAsset(shipId, modelId, "exterior.glb"); service.publicAsset(shipId, modelId, "deck.glb");
        for (String name : List.of("checksums.json", "manifest.json", "other.glb", "../exterior.glb"))
            assertThrows(ResponseStatusException.class, () -> service.publicAsset(shipId, modelId, name));
        verify(store).asset("demo", "v1", "exterior.glb"); verify(store).asset("demo", "v1", "deck.glb");
    }
}
