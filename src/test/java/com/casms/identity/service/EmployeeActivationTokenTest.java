package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.UUID;

class EmployeeActivationTokenTest {
    private final EmployeeActivationToken tokens =
            new EmployeeActivationToken(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void verifiesOnlyTheSignedActivationId() {
        UUID id = UUID.randomUUID();
        String token = tokens.create(id);

        assertEquals(id, tokens.verify(token));
        var tampered = UUID.randomUUID() + token.substring(36);
        assertEquals("EMPLOYEE_ACTIVATION_INVALID",
                assertThrows(BusinessException.class, () -> tokens.verify(tampered)).getCode());
    }
}
