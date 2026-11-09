package com.casms.common.exception;

import com.casms.common.response.ApiResponse;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getCode(), ex.getDetails(), null));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
        String code = resolveNotFoundCode(msg);
        log.warn("Entity not found [{}]: {}", code, msg);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg, code));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        log.warn("Illegal argument: {}", msg);

        String lower = msg.toLowerCase();
        if (lower.contains("already exists") || lower.contains("duplicate")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(msg, "DUPLICATE_CODE"));
        } else if (lower.contains("already assigned")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(msg, "REGULATION_ALREADY_ASSIGNED"));
        } else if (lower.contains("not found")) {
            String code = resolveNotFoundCode(msg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg, code));
        }

        return ResponseEntity.badRequest().body(ApiResponse.error(msg, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Illegal state";
        log.warn("Illegal state: {}", msg);

        String code = "OIM_INVALID_TRANSITION";
        String lower = msg.toLowerCase();
        if (lower.contains("conflict") || lower.contains("blocking")) {
            code = "REGULATION_CONFLICT_BLOCKING";
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(msg, code));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(msg, code));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "Thiếu thông tin bắt buộc.",
                "MISSING_REQUIRED_PARAMETER",
                Map.of("parameter", ex.getParameterName()),
                null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .collect(Collectors.joining(", "));

        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VALIDATION_FAILED", details, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {
        String message =
                ex.getConstraintViolations().stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.error(message, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn(
                "HTTP message not readable (malformed JSON or invalid format): {}",
                ex.getMessage());
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "Dữ liệu gửi lên không đúng định dạng JSON hoặc trường dữ liệu"
                                    + " không hợp lệ",
                                "INVALID_REQUEST_BODY"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource/Endpoint not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.error(
                                "Đường dẫn hoặc tài nguyên không tồn tại: " + ex.getResourcePath(),
                                "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(
                        ApiResponse.error(
                                "Phương thức HTTP không được hỗ trợ: " + ex.getMethod(),
                                "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupported(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(ex.getMessage(), "OPERATION_NOT_SUPPORTED"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required", "AUTH_REQUIRED"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ApiResponse.error(message, status.name()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String msg =
                String.format(
                        "Invalid parameter '%s': value '%s' is not valid for type '%s'",
                        ex.getName(),
                        ex.getValue(),
                        ex.getRequiredType() != null
                                ? ex.getRequiredType().getSimpleName()
                                : "unknown");
        log.warn("Method argument type mismatch: {}", msg);
        return ResponseEntity.badRequest().body(ApiResponse.error(msg, "INVALID_PARAMETER"));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(Exception ex) {
        log.warn("Optimistic lock conflict detected: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("legacyCode", "OPTIMISTIC_LOCK_CONFLICT");
        details.put(
                "reason",
                "Dữ liệu đã bị thay đổi bởi thao tác khác. Vui lòng tải lại trang để lấy revision"
                    + " mới nhất.");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(
                                "Dữ liệu đã bị thay đổi bởi thao tác khác (xung đột phiên bản). Vui"
                                    + " lòng làm mới và thử lại.",
                                "STALE_REVISION",
                                details,
                                null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        Throwable root = ex.getMostSpecificCause();
        String rootMsg =
                root != null && root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        String msg =
                ex.getMessage() != null ? ex.getMessage() : "Dữ liệu xung đột toàn vẹn dữ liệu";
        if (rootMsg.contains("idempotency") || rootMsg.contains("uq_idempotency")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiResponse.error(
                                    "Yêu cầu trùng lặp với cùng Idempotency-Key đang được xử lý"
                                        + " hoặc đã hoàn tất.",
                                    "IDEMPOTENCY_CONFLICT"));
        }
        if (rootMsg.contains("unique")
                || rootMsg.contains("duplicate")
                || rootMsg.contains("uq_")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiResponse.error(
                                    "Dữ liệu trùng lặp trong hệ thống.", "DUPLICATE_RESOURCE"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(
                                "Xung đột ràng buộc dữ liệu: "
                                        + (root != null && root.getMessage() != null
                                                ? root.getMessage()
                                                : msg),
                                "DATA_INTEGRITY_VIOLATION"));
    }

    @ExceptionHandler({
        QueryTimeoutException.class,
        CannotAcquireLockException.class,
        PessimisticLockingFailureException.class,
        DataAccessResourceFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDatabaseLockAndTimeout(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        log.warn("Database lock/timeout exception: {}", ex.getMessage());
        if (msg.contains("statement timeout") || msg.contains("timeout")) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(
                            ApiResponse.error(
                                    "Thao tác xử lý dữ liệu vượt quá thời gian chờ (Database Statement Timeout). Vui lòng kiểm tra dung lượng gói tải lên hoặc thử lại.",
                                    "DATABASE_TIMEOUT"));
        }
        if (msg.contains("lock") || msg.contains("deadlock")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            ApiResponse.error(
                                    "Tài nguyên đang bị khóa bởi một tiến trình khác đang xử lý. Vui lòng thử lại sau giây lát.",
                                    "LOCK_CONFLICT"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ApiResponse.error(
                                "Không thể hoàn tất thao tác cơ sở dữ liệu. Vui lòng thử lại sau.",
                                "DATABASE_RESOURCE_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [traceId: {}]", traceId, ex);
        String msg = (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            msg += " | Cause: " + ex.getCause().getMessage();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(msg, "INTERNAL_ERROR", traceId));
    }

    private String resolveNotFoundCode(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("operational issue") || lower.contains("issue not found")) {
            return "OIM_NOT_FOUND";
        } else if (lower.contains("regulation")) {
            return "REGULATION_NOT_FOUND";
        } else if (lower.contains("ship") || lower.contains("cruise") || lower.contains("host")) {
            return "HOST_NOT_FOUND";
        }
        return "RESOURCE_NOT_FOUND";
    }
}
