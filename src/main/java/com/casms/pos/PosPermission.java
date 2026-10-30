package com.casms.pos;

/** Only implemented service/POS operations may be delegated to a terminal. */
public enum PosPermission {
    SERVICE_READ, SERVICE_REQUEST_READ, SERVICE_REQUEST_ASSIGN, SERVICE_REQUEST_EXECUTE,
    SERVICE_SALE
}
