package com.casms.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String error;
    private String code;
    private Map<String, Object> details;
    private String traceId;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String error, String code) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .code(code)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String error, String code, String traceId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .code(code)
                .traceId(traceId)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String error, String code, Map<String, Object> details, String traceId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .code(code)
                .details(details)
                .traceId(traceId)
                .timestamp(Instant.now())
                .build();
    }
}