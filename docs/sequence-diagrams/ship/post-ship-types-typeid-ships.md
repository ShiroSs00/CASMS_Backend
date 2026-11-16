# POST /ship-types/{typeId}/ships

Ship Manager enters a vessel name and may attach a profile. One transaction creates a DRAFT Ship, shared-asset model reference and ship-specific imports. Rejected structure or duplicate identities roll back creation.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.

## Ship-type change

The reused model becomes READY after structural import and mapping validation. The physical ship remains DRAFT.

See [ship type flow](../SHIP-TYPE-FLOWS.md).
