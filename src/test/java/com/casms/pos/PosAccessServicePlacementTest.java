package com.casms.pos;

import com.casms.common.exception.BusinessException;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PosAccessServicePlacementTest {
    @Test
    void scopedPlacementDoesNotRestoreExplicitlyDisabledAreaLink() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = mock(PosRepository.class);
        var access = new PosAccessService(jdbc, repository, mock(UserRepository.class),
                mock(RolePermissionRepository.class));
        UUID terminal = UUID.randomUUID(), ship = UUID.randomUUID(), area = UUID.randomUUID();
        when(repository.device(terminal, true)).thenReturn(Optional.of(new PosRepository.Device(
                new PosDtos.Terminal(terminal, ship, area, "POS", "Terminal", Set.of(), true), "")));
        when(repository.area(ship, area, true)).thenReturn(Optional.of(
                new PosRepository.Area(area, ship, UUID.randomUUID(), "READY")));
        when(jdbc.queryForList(contains("PosTerminalArea"), eq(Boolean.class), eq(terminal), eq(area)))
                .thenReturn(List.of(false));

        var error = assertThrows(BusinessException.class, () -> access.ensureAreaLinkForPlacement(terminal, area));

        assertEquals("POS_AREA_LINK_DISABLED", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void scopedPlacementKeepsDisabledAreaLinkInsertedConcurrently() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = mock(PosRepository.class);
        var access = new PosAccessService(jdbc, repository, mock(UserRepository.class),
                mock(RolePermissionRepository.class));
        UUID terminal = UUID.randomUUID(), ship = UUID.randomUUID(), area = UUID.randomUUID();
        when(repository.device(terminal, true)).thenReturn(Optional.of(new PosRepository.Device(
                new PosDtos.Terminal(terminal, ship, area, "POS", "Terminal", Set.of(), true), "")));
        when(repository.area(ship, area, true)).thenReturn(Optional.of(
                new PosRepository.Area(area, ship, UUID.randomUUID(), "READY")));
        when(jdbc.queryForList(contains("PosTerminalArea"), eq(Boolean.class), eq(terminal), eq(area)))
                .thenReturn(List.of(), List.of(false));
        when(jdbc.update(contains("DO NOTHING"), eq(terminal), eq(area))).thenReturn(0);

        var error = assertThrows(BusinessException.class, () -> access.ensureAreaLinkForPlacement(terminal, area));

        assertEquals("POS_AREA_LINK_DISABLED", error.getCode());
        verify(jdbc).update(contains("DO NOTHING"), eq(terminal), eq(area));
        verify(jdbc, never()).update(contains("PosSession"), eq(terminal), eq(area));
    }
}
