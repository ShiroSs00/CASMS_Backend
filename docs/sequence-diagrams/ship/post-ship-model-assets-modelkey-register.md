# POST /ship-model-assets/{modelKey}/register

## Ship-type change

Authorized legacy requests return 410 with SHIP_TYPE_IMPORT_REQUIRED and make no database writes. Admin uploads a complete type model through POST /ship-types.

See [ship type flow](../SHIP-TYPE-FLOWS.md).
