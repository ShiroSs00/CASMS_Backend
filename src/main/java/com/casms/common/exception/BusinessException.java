package com.casms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
        this.code = "BUSINESS_ERROR";
        this.details = null;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
        this.code = code;
        this.details = null;
    }

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = null;
    }

    public BusinessException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
