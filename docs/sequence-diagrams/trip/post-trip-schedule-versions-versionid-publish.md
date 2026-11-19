# POST /trip-schedule-versions/{versionId}/publish

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

## Shore excursion destination

Shore excursion entries report their persisted destination. A legacy row without one returns an unset destination. See [the reviewed flow](../ITINERARY-PROGRAM-FLOWS.md#shore-excursion-template-selection).
