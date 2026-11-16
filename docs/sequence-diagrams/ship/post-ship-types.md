# POST /ship-types

Admin uploads a ZIP and model key. ShipTypeService validates the required manifest metadata and saves the linked type atomically with the database assets. Invalid fields reject creation. Returns 201.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.
