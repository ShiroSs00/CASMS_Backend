package com.casms.common.util;

import com.casms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** v4.1: validate new writes; never relabel or convert historical monetary records. */
public final class NewWriteCurrency {
    private NewWriteCurrency() {}
    public static void requireUsd(String currency) {
        if(!"USD".equals(currency))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
            "CURRENCY_USD_REQUIRED","New monetary writes require USD");
    }
}
