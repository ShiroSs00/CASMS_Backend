package com.casms.ship.service;

import com.casms.ship.controller.ShipModelController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StoredModelCatalogTest {
    @Test void previewReturnsModelMetadataWithoutCreatingShip() throws Exception {
        var assets=mock(ShipModelAssetService.class);
        var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"name\":\"Conquest\",\"passengerCapacity\":3000,\"decks\":[],\"cabins\":[]}");
        when(assets.previewStoredBundle("conquest","v10")).thenReturn(manifest);
        var mvc=standaloneSetup(new ShipModelController(mock(ShipModelService.class),assets,mock(ShipModelImportService.class))).build();
        mvc.perform(get("/ship-model-assets/conquest/v10/preview")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.name").value("Conquest"))
            .andExpect(jsonPath("$.data.passengerCapacity").value(3000));
        verify(assets).previewStoredBundle("conquest","v10");
        verifyNoMoreInteractions(assets);
    }

    @Test void returnsStoredBundlesWithOriginalUploadTimestamp() throws Exception {
        var assets=mock(ShipModelAssetService.class);
        when(assets.listStoredBundles()).thenReturn(List.of(new ShipModelAssetStore.StoredBundle("conquest","v10",Instant.parse("2026-10-25T00:00:00Z"))));
        var mvc=standaloneSetup(new ShipModelController(mock(ShipModelService.class),assets,mock(ShipModelImportService.class))).build();
        mvc.perform(get("/ship-model-assets")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].modelKey").value("conquest"))
            .andExpect(jsonPath("$.data[0].assetVersion").value("v10"))
            .andExpect(jsonPath("$.data[0].uploadedAt").exists());
        assertEquals("hasAuthority('SHIP_MODEL_CREATE') and hasAuthority('SHIP_CREATE')",ShipModelController.class.getMethod("storedAssets").getAnnotation(PreAuthorize.class).value());
    }
}
