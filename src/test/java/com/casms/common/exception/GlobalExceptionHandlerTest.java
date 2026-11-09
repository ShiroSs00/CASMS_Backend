package com.casms.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.response.ApiResponse;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusinessException_returnsCorrectStatusAndEnvelope() {
        BusinessException ex =
                new BusinessException(
                        HttpStatus.CONFLICT,
                        "OIM_INVALID_TRANSITION",
                        "Chỉ issue RESOLVED mới có thể đóng.",
                        Map.of("currentStatus", "OPEN", "requiredStatus", "RESOLVED"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Chỉ issue RESOLVED mới có thể đóng.", response.getBody().getError());
        assertEquals("OIM_INVALID_TRANSITION", response.getBody().getCode());
        assertEquals("OPEN", response.getBody().getDetails().get("currentStatus"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleEntityNotFound_returns404AndMappedCode() {
        EntityNotFoundException ex =
                new EntityNotFoundException("Operational issue not found: 123");

        ResponseEntity<ApiResponse<Void>> response = handler.handleEntityNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("OIM_NOT_FOUND", response.getBody().getCode());
    }

    @Test
    void handleIllegalArgument_duplicateCode_returns409() {
        IllegalArgumentException ex =
                new IllegalArgumentException("Issue code already exists: ISS-01");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DUPLICATE_CODE", response.getBody().getCode());
    }

    @Test
    void handleIllegalArgument_validation_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input value");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VALIDATION_FAILED", response.getBody().getCode());
    }

    @Test
    void handleIllegalState_transition_returns409() {
        IllegalStateException ex = new IllegalStateException("Only RESOLVED issues can be closed.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("OIM_INVALID_TRANSITION", response.getBody().getCode());
    }

    @Test
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("ACCESS_DENIED", response.getBody().getCode());
    }

    @Test
    void handleAuthentication_returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("AUTH_REQUIRED", response.getBody().getCode());
    }

    @Test
    void handleDatabaseLockAndTimeout_statementTimeout_returns504GatewayTimeout() {
        var ex = new org.springframework.dao.DataAccessResourceFailureException(
                "PreparedStatementCallback; SQL [SELECT pg_advisory_xact_lock(...)]; ERROR: canceling statement due to statement timeout");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDatabaseLockAndTimeout(ex);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DATABASE_TIMEOUT", response.getBody().getCode());
        assertTrue(response.getBody().getError().contains("Database Statement Timeout"));
    }

    @Test
    void handleDatabaseLockAndTimeout_lockConflict_returns409Conflict() {
        var ex = new org.springframework.dao.CannotAcquireLockException("could not obtain lock on relation");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDatabaseLockAndTimeout(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("LOCK_CONFLICT", response.getBody().getCode());
    }

    @Test
    void handleGeneral_returns500WithTraceId() {
        Exception ex = new RuntimeException("Unexpected db crash");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
        assertNotNull(response.getBody().getTraceId());
    }
}
