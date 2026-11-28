# POST /pos/sales/reviewed

Route security uses `hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked`.

The reviewed sale validates the quoted catalogue price, then uses the same transactional POS sale and service-request path as POST /pos/sales. Exact hours are checked before recording a new sale.

See [exact daily service hours](../PORT-CALL-BOOKING-FLOWS.md#exact-daily-service-hours).
