# POST /ships

## Ship-type change

Authorized legacy requests return 410 with SHIP_CREATE_FROM_TYPE_REQUIRED and make no database writes. Use POST /ship-types/{typeId}/ships.

See [ship type flow](../SHIP-TYPE-FLOWS.md).
