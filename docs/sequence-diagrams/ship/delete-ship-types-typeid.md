# DELETE /ship-types/{typeId}

Admin deletes an unused type. The same lock as vessel provisioning serializes the dependency check. An assigned type returns 409. Success returns 204.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
