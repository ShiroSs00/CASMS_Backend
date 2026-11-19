# POST /checkin/passenger/{passengerId}/start

## Online check-in window

Customer requests require ownership and an active passenger on a confirmed booking. Online mutations are allowed from exactly T−3h until scheduled departure. Outside that window the service returns 409 CHECKIN_WINDOW_CLOSED and exposes opensAt/closesAt. The service uses the current scheduled departure and serializes writes on the booking passenger. Existing authorized staff handling remains available.

Starts an in-progress record and updates contact fields. The customer's completed check-in is preserved.

[Reviewed sequence](../ONLINE-CHECKIN-NFC-CARD.md).
