package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.ModelImportJobRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipModelRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

class ShipModelDeletionTest {
    private final UUID shipId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();
    private final ShipModelRepository models = mock(ShipModelRepository.class);
    private final ShipModelObjectRepository objects = mock(ShipModelObjectRepository.class);
    private final ModelImportJobRepository importJobs = mock(ModelImportJobRepository.class);
    private final ShipModelService service =
            new ShipModelService(null, models, objects, importJobs, null, null, null, null);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCanDeleteAnUnusedDraft() {
        authenticateManager(false);
        ShipModel model = lockedModel(ShipModelStatus.DRAFT);

        service.deleteDraft(shipId, modelId);

        verify(models).delete(model);
    }

    @ParameterizedTest
    @EnumSource(value = ShipModelStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void managerCannotDeleteNonDraftModels(ShipModelStatus status) {
        authenticateManager(false);
        ShipModel model = lockedModel(status);

        assertEquals("SHIP_MODEL_IN_USE",
                assertThrows(BusinessException.class, () -> service.deleteDraft(shipId, modelId)).getCode());
        verify(models, never()).delete(model);
    }

    @Test
    void managerCannotDeleteDraftWithMappedObjects() {
        authenticateManager(false);
        ShipModel model = lockedModel(ShipModelStatus.DRAFT);
        when(objects.existsByShipModelShipModelId(modelId)).thenReturn(true);

        assertEquals("SHIP_MODEL_HAS_OBJECTS",
                assertThrows(BusinessException.class, () -> service.deleteDraft(shipId, modelId)).getCode());
        verify(models, never()).delete(model);
    }

    @Test
    void managerCannotDeleteDraftWithImportHistory() {
        authenticateManager(false);
        ShipModel model = lockedModel(ShipModelStatus.DRAFT);
        when(importJobs.existsByShipModelShipModelId(modelId)).thenReturn(true);

        assertEquals("SHIP_MODEL_IN_USE",
                assertThrows(BusinessException.class, () -> service.deleteDraft(shipId, modelId)).getCode());
        verify(models, never()).delete(model);
    }

    @Test
    void managerCannotDeleteModelThroughWrongShip() {
        authenticateManager(false);
        ShipModel model = lockedModel(ShipModelStatus.DRAFT);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteDraft(UUID.randomUUID(), modelId));
        verify(models, never()).delete(model);
    }

    @Test
    void adminWithManagerRoleRetainsFailedModelDeletion() {
        authenticateManager(true);
        ShipModel model = lockedModel(ShipModelStatus.FAILED);

        service.deleteDraft(shipId, modelId);

        verify(models).delete(model);
    }

    private void authenticateManager(boolean admin) {
        CasmsPrincipal actor = mock(CasmsPrincipal.class);
        when(actor.getUserId()).thenReturn(UUID.randomUUID());
        when(actor.hasRole(Role.SHIP_MANAGER)).thenReturn(true);
        when(actor.hasRole(Role.ADMIN)).thenReturn(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, null));
    }

    private ShipModel lockedModel(ShipModelStatus status) {
        ShipModel model = new ShipModel();
        model.setStatus(status);
        when(models.lockModelForShip(shipId, modelId)).thenReturn(Optional.of(model));
        return model;
    }
}
