# POST /servicepackage/requests

## Port-call and booking

See the [reviewed flow](../PORT-CALL-BOOKING-FLOWS.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

New usage checks exact catalog hours before recording a request or POS sale. See [exact daily service hours](../PORT-CALL-BOOKING-FLOWS.md#exact-daily-service-hours).
