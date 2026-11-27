# GET /ships/{shipId}/operational-profile

The scoped Ship Manager reads the typed ship profile JSONB in a read-only transaction. The response includes `lastClassificationDate` when recorded. A missing profile returns an empty result. A missing ship returns 404 and a legacy ship returns 409. See [ship type flow](../SHIP-TYPE-FLOWS.md).
