# PUT /ship-types/{typeId}

Admin replaces a type model with a complete ZIP. The type row is locked and the asset revision and metadata change in one transaction. Existing ship assignments retain their model version.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
