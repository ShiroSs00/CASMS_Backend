# POST /ship-types/{typeId}/ships

Ship Manager enters a vessel name and a complete profile. The service checks certification, warranty, maintenance and expiry before allocating a ship code. An incomplete or expired profile returns 422. One transaction provisions the ship, activates its model and returns 201 with the ship READY. Rejected structure or duplicate identities roll back creation.

The JSON decoder reads the survey date from `profile.classificationDate` before validation. Future dates or an expiry before the survey date return 400. The creation transaction writes the date to the ship's existing profile JSONB when present. Read `classificationDate` via GET operational profile.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.

## Ship-type change

The model and physical ship become READY after structural import and mapping validation. Replay of a previously committed request returns its existing ship, including an older DRAFT ship.

See [ship type flow](../SHIP-TYPE-FLOWS.md).

On first insert, JPA derives commissionDate from createdAt in Asia/Ho_Chi_Minh and saves both in the provisioning transaction. The 201 response includes commissionDate, and a replay returns the saved date.
