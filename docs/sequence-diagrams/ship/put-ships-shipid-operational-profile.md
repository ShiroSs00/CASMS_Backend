# PUT /ships/{shipId}/operational-profile

Ship Manager with ship access replaces the profile. The service locks the ship and rejects legacy ships for this endpoint. PostgreSQL enforces normalized identity uniqueness.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
