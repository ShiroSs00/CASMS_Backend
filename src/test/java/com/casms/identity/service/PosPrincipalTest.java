package com.casms.identity.service;

import com.casms.identity.entity.User;
import com.casms.identity.enums.*;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosPrincipalTest {
    @Test void deviceIdentityCannotAcquireEmployeeOrAdministrativeRightsFromMixedRoles() {
        User user=new User();user.setRoles(Set.of(Role.POS,Role.ADMIN,Role.ONBOARD_STAFF));
        var repository=mock(RolePermissionRepository.class);
        var principal=new CasmsPrincipal(user,repository);
        assertEquals(Set.of("POS","ROLE_POS"),principal.getAuthorities().stream().map(a->a.getAuthority()).collect(Collectors.toSet()));
        assertFalse(principal.isStaff());assertFalse(principal.isAdmin());
        assertFalse(principal.hasRole(Role.ADMIN));assertFalse(principal.hasAnyRole(Role.ONBOARD_STAFF,Role.ADMIN));
        assertFalse(principal.hasAllRoles(Role.POS,Role.ADMIN));assertTrue(principal.hasRole(Role.POS));
        assertFalse(principal.hasPermission(Permission.POS_ACCESS));
        assertFalse(principal.hasPermission(Permission.PAYMENT_PROCESS));
        verifyNoInteractions(repository);
    }
}
