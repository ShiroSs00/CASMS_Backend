package com.casms.identity.controller;

import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
@RequiredArgsConstructor
public class AuthExceptionHandler {

    private final GlobalExceptionHandler globalExceptionHandler;

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationPersistenceFailure(RuntimeException ex) {
        return globalExceptionHandler.handleGeneral(ex);
    }
}
