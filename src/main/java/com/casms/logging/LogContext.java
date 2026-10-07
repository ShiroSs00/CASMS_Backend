package com.casms.logging;

import org.slf4j.MDC;

public final class LogContext {

    private LogContext() {}

    public static String getCorrelationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    public static void setCorrelationId(String correlationId) {
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.clear();
    }
}
