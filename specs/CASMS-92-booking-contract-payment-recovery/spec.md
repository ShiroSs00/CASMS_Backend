# CASMS-92: physical contract and secure booking follow-up

Source: user request and three supplied audit screenshots, 2026-11-10.

- Customers meet staff and sign a physical contract; authorized staff record only references, signer and actual signing date.
- Accepted contract plus verified deposit/full collection allows registration confirmation; confirmation queues email.
- Passenger accounts must belong to the customer, never the staff member entering the booking. Existing identity cannot be overwritten by later trips.
- The website can read owned bookings, verified payment balance, installments and per-trip guidance using bookingPassengerId.
- Recovery requires original quote guest token or the authenticated actor's own idempotency namespace.
- Completion persistence failure must roll back booking and all related writes; retry must never create duplicates.

Acceptance: key alone cannot read any booking; wrong actor/token fails; valid proof gets current booking; old uncertain reservations are never expired into retries; confirmed email is queued once; SMTP failure retries; account setup token is single-use and cannot reset established account passwords; contract notes are rejected.

Implementation choice: SMTP/Gmail-compatible transport, disabled until configured; frontend supplies an activation screen. No live email, DB writes or migration execution is authorized by local implementation.
