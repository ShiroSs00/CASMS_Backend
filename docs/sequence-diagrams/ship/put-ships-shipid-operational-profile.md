# PUT /ships/{shipId}/operational-profile

Ship Manager with ship access replaces the profile. The service locks the ship and rejects legacy ships for this endpoint. PostgreSQL enforces normalized identity uniqueness.

The JSON decoder reads the survey date from `classificationDate` before validation. An expiry before the survey date or a future survey date returns 400. The replacement transaction stores the date in the existing JSONB and returns `classificationDate`. Omitting the date clears it.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
