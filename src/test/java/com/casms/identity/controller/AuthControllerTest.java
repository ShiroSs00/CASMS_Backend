package com.casms.identity.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.identity.service.AuthService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final GlobalExceptionHandler globalErrors = new GlobalExceptionHandler();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(globalErrors, new AuthExceptionHandler(globalErrors))
            .build();

    @Test
    void refreshReturnsGenericServerErrorWhenAccountLookupFails() throws Exception {
        when(authService.refreshToken("test-token"))
                .thenThrow(new CannotAcquireLockException("Private database detail"));

        mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").value("Đã xảy ra lỗi nội bộ."))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void loginRejectsPasswordOverBcryptByteLimit() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"employee\",\"password\":\"" + "é".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRejectsMissingAuthorization() throws Exception {
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "abc", "Bearer "})
    void refreshRejectsInvalidAuthorization(String authorization) throws Exception {
        mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(authService);
    }
}
