package com.casms.ship.service;

import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipTypeServiceTest {
    @Mock ShipTypeRepository types;
    @Mock ShipRepository ships;
    @Mock ShipModelAssetStore assets;
    @Mock ShipModelAssetService modelAssets;
    @Mock ShipModelImportService importer;
    @Mock BusinessCodeGenerator codes;
    @Mock ShipService shipService;
    @InjectMocks ShipTypeService service;

    @Test
    void anIncompleteManifestDoesNotCreateAnyTypeOrShip() throws Exception {
        var bundle = new MockMultipartFile("bundle", new byte[] {1});
        when(assets.storeImportZip("voyager", bundle)).thenReturn("version");
        when(assets.manifest("voyager", "version")).thenReturn(new ObjectMapper().readTree(
                """
                {"modelId":"voyager","name":"Voyager"}
                """));

        var error = assertThrows(ResponseStatusException.class, () -> service.create("voyager", bundle));
        assertTrue(error.getReason().contains("shipManufacturerName"));
        verifyNoInteractions(types, ships, modelAssets, importer);
    }

    @Test
    void provisioningUsesManagerNameAndPinnedAsset() throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID shipId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        ShipType type = new ShipType();
        type.setShipTypeId(typeId);
        type.setModelKey("voyager");
        type.setAssetVersion("pinned");
        type.setTotalDecks(6);
        when(types.findForUpdate(typeId)).thenReturn(Optional.of(type));
        when(assets.manifest("voyager", "pinned")).thenReturn(new ObjectMapper().readTree(
                """
                {"modelId":"voyager","name":"Type name","decks":[{"number":7}]}
                """));
        when(codes.next("SH")).thenReturn("SH-0001");
        when(ships.saveAndFlush(any(Ship.class))).thenAnswer(call -> {
            Ship ship = call.getArgument(0);
            assertEquals("Blue Dolphin", ship.getShipName());
            assertEquals("SH-0001", ship.getShipCode());
            assertEquals(7, ship.getDeckNumberLimit());
            assertSame(type, ship.getShipType());
            assertEquals(ShipStatus.DRAFT, ship.getStatus());
            assertNull(ship.getOperationalProfile());
            ship.setShipId(shipId);
            return ship;
        });
        when(modelAssets.register(shipId, "voyager", "pinned")).thenReturn(modelId);

        service.createShip(typeId, new ShipFromTypeRequest("Blue Dolphin", null), "manager");
        verify(importer).importForProvisioning(shipId, modelId, "manager");
        verify(modelAssets).activate(shipId, modelId);
        verify(shipService).detail(shipId);
    }
}
