package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.trip.entity.IdempotencyRecord;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipTypeRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
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
    @Spy ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
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
        var profile = completeProfile(LocalDate.now(PortTimeZoneHelper.SHIP_TIME_ZONE));
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
            assertEquals("voyager", ship.getDesignModelKey());
            assertEquals("pinned", ship.getDesignAssetVersion());
            assertEquals(profile, ship.getOperationalProfile());
            ship.setShipId(shipId);
            return ship;
        });
        when(modelAssets.register(shipId, "voyager", "pinned")).thenReturn(modelId);

        UUID requestKey = UUID.randomUUID();
        var principal = manager();
        UUID managerId = principal.getUserId();
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
            service.createShip(typeId, new ShipFromTypeRequest("Blue Dolphin", profile), "manager", requestKey);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(shipId, reservation.getResultingId());
        var saved = org.mockito.ArgumentCaptor.forClass(Ship.class);
        verify(ships).saveAndFlush(saved.capture());
        assertEquals(ShipStatus.READY, saved.getValue().getStatus());
        verify(importer).importForProvisioning(shipId, modelId, "manager");
        verify(modelAssets).activate(shipId, modelId);
        verify(shipService).detail(shipId);
    }

    @Test
    void incompleteProfileStopsProvisioningBeforeAllocatingShipCodeOrWritingShip() throws Exception {
        rejectNewProvisioning(null, "SHIP_OPERATIONAL_PROFILE_INCOMPLETE");
    }

    @Test
    void expiredProfileStopsProvisioningBeforeWritingShip() throws Exception {
        rejectNewProvisioning(
                completeProfile(LocalDate.now(PortTimeZoneHelper.SHIP_TIME_ZONE).minusDays(1)),
                "SHIP_CLASSIFICATION_EXPIRED");
    }

    @Test
    void replayOfExistingDraftDoesNotRequireACompleteProfile() {
        UUID typeId = UUID.randomUUID();
        UUID shipId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var principal = manager();
        var reservation = new IdempotencyRecord();
        reservation.setResultingId(shipId);
        stubReservation(principal.getUserId(), key, reservation, 0);
        ShipResponse draft = new ShipResponse();
        draft.setShipId(shipId);
        draft.setStatus(ShipStatus.DRAFT);
        when(shipService.detail(shipId)).thenReturn(draft);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertSame(draft, service.createShip(typeId,
                    new ShipFromTypeRequest("Legacy ship", null), "manager", key));
        } finally {
            SecurityContextHolder.clearContext();
        }
        verifyNoInteractions(types, ships, assets, codes, importer, modelAssets);
    }

    private void rejectNewProvisioning(ShipOperationalProfile profile, String code) throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var principal = manager();
        var reservation = new IdempotencyRecord();
        stubReservation(principal.getUserId(), key, reservation, 1);
        ShipType type = new ShipType();
        type.setModelKey("voyager");
        type.setAssetVersion("pinned");
        when(types.findForUpdate(typeId)).thenReturn(Optional.of(type));
        var manifest = mapper.readTree("{}");
        when(assets.manifest("voyager", "pinned")).thenReturn(manifest);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            BusinessException failure = assertThrows(BusinessException.class,
                    () -> service.createShip(typeId, new ShipFromTypeRequest("Blue Dolphin", profile),
                            "manager", key));
            assertEquals(code, failure.getCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
        verifyNoInteractions(codes, ships, importer, modelAssets);
    }

    private void stubReservation(UUID managerId, UUID key, IdempotencyRecord reservation, int inserted) {
        when(idempotencyRecords.reserveShipProvisioning(any(), eq(managerId), eq(key.toString()), anyString()))
                .thenAnswer(call -> {
                    reservation.setRequestHash(call.getArgument(3));
                    return inserted;
                });
        when(idempotencyRecords.findByActorIdAndOperationAndIdempotencyKey(
                managerId, "PROVISION_SHIP", key.toString())).thenReturn(Optional.of(reservation));
    }

    private static CasmsPrincipal manager() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setRoles(java.util.Set.of(Role.SHIP_MANAGER));
        return new CasmsPrincipal(user, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deliveryDate", "imoNumber", "registrationCountry", "registrationNumber",
            "classificationSociety", "classificationDate", "classificationExpiryDate"})
    void replacementRejectsEachMissingRequiredField(String field) throws Exception {
        UUID shipId = UUID.randomUUID();
        var previous = completeReplacementProfile();
        Ship ship = new Ship();
        ship.setShipType(new ShipType());
        ship.setStatus(ShipStatus.READY);
        ship.setOperationalProfile(previous);
        when(ships.findByIdForUpdate(shipId)).thenReturn(Optional.of(ship));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(previous);
        payload.remove(field);
        var incomplete = mapper.treeToValue(payload, ShipOperationalProfile.class);

        var failure = assertThrows(BusinessException.class, () -> service.replaceProfile(shipId, incomplete));

        assertEquals("SHIP_OPERATIONAL_PROFILE_INCOMPLETE", failure.getCode());
        assertSame(previous, ship.getOperationalProfile());
        assertEquals(ShipStatus.READY, ship.getStatus());
    }

    @ParameterizedTest
    @EnumSource(ShipStatus.class)
    void allShipStatusesRequireCompleteReplacementButAllowEmptyReferences(ShipStatus status) throws Exception {
        UUID shipId = UUID.randomUUID();
        Ship ship = new Ship();
        ship.setShipType(new ShipType());
        ship.setStatus(status);
        when(ships.findByIdForUpdate(shipId)).thenReturn(Optional.of(ship));
        var incomplete = mapper.readValue("{}", ShipOperationalProfile.class);
        var complete = completeReplacementProfile();

        assertEquals("SHIP_OPERATIONAL_PROFILE_INCOMPLETE",
                assertThrows(BusinessException.class, () -> service.replaceProfile(shipId, incomplete)).getCode());
        assertNull(ship.getOperationalProfile());
        assertEquals(complete, service.replaceProfile(shipId, complete));
        assertEquals(complete, ship.getOperationalProfile());
        assertEquals(status, ship.getStatus());
    }

    private ShipOperationalProfile completeReplacementProfile() throws Exception {
        return mapper.readValue("""
                {"deliveryDate":"2024-01-15","imoNumber":"1234567","registrationCountry":"VN",
                 "registrationNumber":"TEST-01","classificationSociety":"Registry",
                 "classificationDate":"2024-03-15","classificationExpiryDate":"2099-03-15",
                 "warrantyReferences":[]}
                """, ShipOperationalProfile.class);
    }

    private static ShipOperationalProfile completeProfile(LocalDate expiry) {
        return new ShipOperationalProfile(null, null, null, null, null, null, null, null,
                "Registry", null, expiry, List.of("certificate"), List.of("warranty"), List.of("maintenance"));
    }
}
