# PUT /ships/{shipId}/operational-profile

Ship Manager with ship access replaces the profile. The service locks the ship and rejects legacy ships for this endpoint. PostgreSQL enforces normalized identity uniqueness.

The JSON decoder reads the survey date from `classificationDate` before validation. An expiry before the survey date or a future survey date returns 400. The replacement transaction stores the date in the existing JSONB and returns `classificationDate`.

Regardless of ship status, replacement requires `deliveryDate`, `imoNumber`, `registrationCountry`, `registrationNumber`, `classificationSociety`, `classificationDate` and `classificationExpiryDate`. The certification, warranty and maintenance reference lists may be omitted or empty. Missing required fields return 422 `SHIP_OPERATIONAL_PROFILE_INCOMPLETE` with `details.missingFields` before the stored profile changes. A registration country/number pair that fails DTO validation returns 400.

See the [reviewed ship-type sequence](../SHIP-TYPE-FLOWS.md) for security, the transaction and related operations.

The delivery date may precede the system commissioning date. Existing validation and the cruise-related delivery-date lock still apply.
