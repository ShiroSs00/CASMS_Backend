# POST /checkin/passenger/{passengerId}/complete

## Online check-in window

Customer requests require ownership and an active passenger on a confirmed booking. Online mutations are allowed from exactly T−3h until scheduled departure. Outside that window the service returns 409 CHECKIN_WINDOW_CLOSED and exposes opensAt/closesAt. The service uses the current scheduled departure and serializes writes on the booking passenger. Existing authorized staff handling remains available.

Requires an accepted contract and an emergency name/phone. A completed request is idempotent for an eligible passenger. Successful completion records BoardingReady.

[Reviewed sequence](../ONLINE-CHECKIN-NFC-CARD.md).
