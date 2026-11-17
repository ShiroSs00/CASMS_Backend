package com.casms.ship.service;

import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Mock IdempotencyRecordRepository idempotencyRecords;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @InjectMocks ShipTypeService service;

    @ParameterizedTest
    @ValueSource(strings = {"name", "shipManufacturerName", "engineType"})
    void anIncompleteManifestDoesNotCreateAnyTypeOrShip(String missingField) throws Exception {
        var bundle = new MockMultipartFile("bundle", new byte[] {1});
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
                {"modelId":"voyager","name":"Voyager","shipManufacturerName":"Shipyard","engineType":"Diesel"}
                """);
        manifest.remove(missingField);
        when(assets.storeImportZip("voyager", bundle)).thenReturn("version");
        when(assets.manifest("voyager", "version")).thenReturn(manifest);

        var error = assertThrows(ResponseStatusException.class, () -> service.create("voyager", bundle));
        assertTrue(error.getReason().contains(missingField));
        verify(types, never()).saveAndFlush(any());
        verifyNoInteractions(ships, modelAssets, importer);
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
        type.setTotalDecks(8);
        when(types.findForUpdate(typeId)).thenReturn(Optional.of(type));
        when(assets.manifest("voyager", "pinned")).thenReturn(new ObjectMapper().readTree(
                """
                {"modelId":"voyager","name":"Type name","decks":[{"number":10}]}
                """));
        when(codes.next("SH")).thenReturn("SH-0001");
        when(ships.saveAndFlush(any(Ship.class))).thenAnswer(call -> {
            Ship ship = call.getArgument(0);
            assertEquals("Blue Dolphin", ship.getShipName());
            assertEquals("SH-0001", ship.getShipCode());
            assertEquals(10, ship.getDeckNumberLimit());
            assertSame(type, ship.getShipType());
            assertEquals(ShipStatus.DRAFT, ship.getStatus());
            assertNull(ship.getOperationalProfile());
            ship.setShipId(shipId);
            return ship;
        });
        when(modelAssets.register(shipId, "voyager", "pinned")).thenReturn(modelId);

        UUID requestKey = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        User user = new User();
        user.setUserId(managerId);
        user.setRoles(java.util.Set.of(Role.SHIP_MANAGER));
        var principal = new CasmsPrincipal(user, null);
        var reservation = new IdempotencyRecord();
        when(idempotencyRecords.reserveShipProvisioning(any(), eq(managerId), eq(requestKey.toString()), anyString()))
                .thenAnswer(call -> {
                    reservation.setRequestHash(call.getArgument(3));
                    return 1;
                });
        when(idempotencyRecords.findByActorIdAndOperationAndIdempotencyKey(
                managerId, "PROVISION_SHIP", requestKey.toString())).thenReturn(Optional.of(reservation));
        ShipResponse response = new ShipResponse();
        response.setShipId(shipId);
        when(shipService.detail(shipId)).thenReturn(response);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            service.createShip(typeId, new ShipFromTypeRequest("Blue Dolphin", null), "manager", requestKey);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(shipId, reservation.getResultingId());
        verify(importer).importForProvisioning(shipId, modelId, "manager");
        verify(modelAssets).activate(shipId, modelId);
        verify(shipService).detail(shipId);
    }
}
