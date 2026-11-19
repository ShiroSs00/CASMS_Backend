# POST /checkin/passenger/{passengerId}/card-claim

**Authorization** · hasRole('PASSENGER') and hasAuthority('CHECKIN_READ').

Verify ownership, BoardingReady, confirmed booking and active passenger. Lock the booking passenger, then upsert the hashed claim with a 10-minute expiry. Return the opaque code and expiry only to the owner.

See [the sequence and transaction boundaries](../ONLINE-CHECKIN-NFC-CARD.md).
