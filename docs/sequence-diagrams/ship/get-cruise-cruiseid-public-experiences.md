# GET /cruise/{cruiseId}/public-experiences

Route security uses `permitAll()`.

The public handler uses JdbcTemplate to read the services assigned to the cruise ship decks. The service objects expose operatingWindows as a JSON array or null for legacy data.

See [exact daily service hours](../PORT-CALL-BOOKING-FLOWS.md#exact-daily-service-hours).

The same handler also serves `GET /cruises/{cruiseId}/public-experiences`.
