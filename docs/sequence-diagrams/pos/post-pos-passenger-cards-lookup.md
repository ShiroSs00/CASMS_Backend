# POST /pos/passenger-cards/lookup

**Authorization** · POS_ACCESS and CHECKIN_BOARDING plus enabled/unlocked principal.

Validate X-POS-Session and query by UID plus selected cruise. Return a minimal passenger projection for confirmed bookings and active passengers.

See [the sequence and transaction boundaries](../ONLINE-CHECKIN-NFC-CARD.md).
