# POST /pos/passenger-cards/confirm

**Authorization** · POS_ACCESS and CHECKIN_BOARDING plus enabled/unlocked principal.

Within one transaction, validate POS session again, lock the passenger, consume the claim and bind the NFC identifier. A duplicate or invalid claim aborts the write. Include the audit actor and timestamp in storage.

See [the sequence and transaction boundaries](../ONLINE-CHECKIN-NFC-CARD.md).
