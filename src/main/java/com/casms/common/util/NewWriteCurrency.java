package com.casms.common.util;

import com.casms.common.exception.BusinessException;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Currency validation helpers for catalog and monetary writes; preserves historical currency records. */
public final class NewWriteCurrency {
    private NewWriteCurrency() {}

    public static void requireUsd(String currency) {
        if (!"USD".equals(currency))
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CURRENCY_USD_REQUIRED",
                    "New monetary writes require USD");
    }

    public static String requireSupportedCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"VND".equals(normalized) && !"USD".equals(normalized)) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_CURRENCY",
                    "Đơn vị tiền tệ không hợp lệ. Hệ thống chỉ hỗ trợ VND hoặc USD.");
        }
        return normalized;
    }

    public static String normalizeOrDefaultVnd(String currency) {
        if (currency == null || currency.isBlank()) {
            return "VND";
        }
        return requireSupportedCurrency(currency);
    }
}
