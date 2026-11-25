# GET /cruise/{cruiseId}

## Ship schedule and fleet

See the [reviewed flow](../TRIP-DEPARTURE-WINDOW.md) for authorization, validation, transaction boundaries and responses. This note covers the BE-09 to BE-20 change.

For newly materialized cruises, endpoint ports follow the [source port inheritance flow](post-cruise.md#source-port-inheritance). If the template has no departure port, the saved itinerary departure facts come from the selected cruise port.
