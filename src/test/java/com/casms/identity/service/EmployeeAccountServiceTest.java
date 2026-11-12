package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class EmployeeAccountServiceTest {
    @Mock private UserRepository users;
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private EmployeeAccountService service;

    @Test
    void adminCanCreateAnySystemRole() {
        var admin = actor();
        when(admin.hasRole(Role.ADMIN)).thenReturn(true);
        assertEquals(java.util.List.of(Role.values()), service.assignableRoles(admin));
    }

    @Test
    void hrCannotProvisionAdminEvenThroughService() {
        var hr = actor();
        var input = new EmployeeAccountService.CreateInput("E01", "Nhân viên", "staff@example.org",
                null, null, null, null, null, null, Role.ADMIN);
        var error = assertThrows(BusinessException.class, () -> service.create(input, hr));
        assertEquals("EMPLOYEE_ROLE_FORBIDDEN", error.getCode());
        verifyNoInteractions(jdbc, users);
    }

    private CasmsPrincipal actor() {
        var actor = mock(CasmsPrincipal.class);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isAccountNonLocked()).thenReturn(true);
        when(actor.hasAnyRole(Role.ADMIN, Role.HR_MANAGER)).thenReturn(true);
        when(actor.hasAuthority("USER_CREATE")).thenReturn(true);
        return actor;
    }
}
