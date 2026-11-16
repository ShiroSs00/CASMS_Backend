# PATCH /ship-types/{typeId}/status

Admin supplies ACTIVE or INACTIVE. The service locks the type and updates status. Missing type returns 404. Inactive types cannot create new ships.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
