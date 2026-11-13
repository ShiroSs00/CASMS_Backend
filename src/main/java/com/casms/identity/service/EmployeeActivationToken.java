package com.casms.identity.service;

import com.casms.common.exception.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Component
public class EmployeeActivationToken {
    private final byte[] key;

    public EmployeeActivationToken(@Value("${casms.employee-activation-email.token-key:}") String configuredKey) {
        key = configuredKey.isBlank() ? null : Base64.getDecoder().decode(configuredKey);
        if (key != null && key.length < 32) {
            throw new IllegalArgumentException("Employee activation key must contain at least 32 bytes");
        }
    }

    public String create(UUID activationId) {
        return activationId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature(activationId));
    }

    public boolean isConfigured() {
        return key != null;
    }

    public UUID verify(String token) {
        if (token == null || token.length() != 80 || token.charAt(36) != '.') throw invalid();
        try {
            UUID id = UUID.fromString(token.substring(0, 36));
            byte[] actual = Base64.getUrlDecoder().decode(token.substring(37));
            if (MessageDigest.isEqual(signature(id), actual)) return id;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
        throw invalid();
    }

    private byte[] signature(UUID id) {
        if (key == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "EMPLOYEE_ACTIVATION_UNAVAILABLE",
                    "Kích hoạt tài khoản nhân viên chưa được cấu hình.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("casms-employee-activation:" + id).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Unable to sign employee activation link", unavailable);
        }
    }

    static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "EMPLOYEE_ACTIVATION_INVALID",
                "Liên kết kích hoạt không hợp lệ hoặc đã hết hạn.");
    }
}
