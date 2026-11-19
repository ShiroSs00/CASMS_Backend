# POST /pos/passenger-cards/preview

**Authorization** · POS_ACCESS and CHECKIN_BOARDING plus enabled/unlocked principal.

Validate X-POS-Session, use the selected cruise, and check the claim is unexpired and unused. Return passenger ID and name for review.

See [the sequence and transaction boundaries](../ONLINE-CHECKIN-NFC-CARD.md).
