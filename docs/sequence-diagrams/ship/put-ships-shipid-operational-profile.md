# PUT /ships/{shipId}/operational-profile

Ship Manager with ship access replaces the profile. The service locks the ship and rejects legacy ships for this endpoint. PostgreSQL enforces normalized identity uniqueness.

The optional `lastClassificationDate` must be on or before the current date. An expiry before the survey date or a future survey date returns 400. The replacement transaction stores the submitted date in the existing JSONB. Omitting the date clears it.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
