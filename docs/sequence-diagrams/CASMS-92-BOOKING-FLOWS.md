# CASMS-92: booking, physical contract, payment and passenger access

Reviewed against the changed implementation. Unchanged route branches retain their existing review status.
All paths below are relative to `/api`.

## Booking creation and recovery

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT / Spring Security
    participant Controller as BookingController
    participant Idem as IdempotencyService
    participant Service as BookingService
    participant Repository as JPA repositories
    participant DB as PostgreSQL
    Client->>Security: POST /booking + Idempotency-Key + quote + guest proof or JWT
    Security->>Controller: Public route, optional authenticated principal
    Controller->>Idem: executeBookingCreate(actor, key, request, action)
    Note over Idem,DB: One transaction: reservation, booking, passengers, cabins, contract, installments and completion
    Idem->>Repository: reserveBookingKey / native INSERT ON CONFLICT DO NOTHING
    Repository->>DB: Unique actor/op/key; partial unique guest op/key
    alt Same key, different canonical request hash
        Idem-->>Client: 409 IDEMPOTENCY_CONFLICT
    else Completed same request
        Idem-->>Controller: Cached original creation DTO, no repeated writes
    else Legacy uncertain reservation
        Idem-->>Client: 409; never expire or create another booking
    else New reservation
        Idem->>Service: createBooking
        Service->>Repository: Validate quote ownership, expiry, policy, sale and capacity; lock cruise and cabins
        Service->>Repository: Resolve customer account, not staff account
        Service->>DB: Advisory transaction lock on normalized email, unique normalized-email constraint
        Note over Service,DB: Existing Passenger identity cannot be overwritten by another trip
        Service->>DB: Persist booking, bookingPassenger IDs, cabin/package assignments, contract references and installments
        Service->>DB: New guest account setup marker, in-app signing invitation
        Service-->>Idem: BookingDetailResponse
        Idem->>DB: Persist resulting booking ID and serialized response in same transaction
        alt Completion/serialization fails
            Idem-->>Client: 500 IDEMPOTENCY_COMPLETION_FAILED; entire transaction rolls back
        else All writes succeed
            DB-->>Client: Commit then 201 response
        end
    end
    Client->>Controller: GET /booking/recovery + key + JWT or X-Guest-Token
    Controller->>Service: recoverBookingByIdempotencyKey
    Service->>Repository: Query actor-scoped CREATE_BOOKING record
    alt No principal and no guest token
        Service-->>Client: 401 RECOVERY_OWNERSHIP_REQUIRED
    else Missing scoped record or new transaction not committed
        Service-->>Client: 404 RECOVERY_NOT_FOUND
    else No resulting ID / legacy uncertain result
        Service-->>Client: 409 RECOVERY_IN_PROGRESS; no re-execution
    else Result found
        Service->>Repository: Load current Booking, not cached business data
        alt Authenticated owner check or original guest quote token fails
            Service-->>Client: 403; legacy guest without quote proof fails closed
        else Ownership verified
            Service->>Repository: Read current passengers, contract, installments, verified payments and completed refunds
            Service-->>Client: 200 current booking + payment balance
        end
    end
```

Actor-scoped recovery does not grant cross-user access merely for `BOOKING_READ`. After signing in, a guest-origin request can still use its original guest token to fall back to the guest namespace when no actor-scoped record exists.
Guest proof is the original quote token, supplied in a header, never the idempotency key alone.
Legacy duplicate guest keys must be reconciled manually before applying V94; the migration refuses to discard records.
Duplicate normalized account emails also require reconciliation before V94; no customer records are deleted or merged automatically.
Existing old reservations without durable outcomes remain uncertain and require operator reconciliation; elapsed time is not evidence of failure.

## Contract, confirmation and email

```mermaid
sequenceDiagram
    actor Staff
    participant Security as JWT + method authorization
    participant Controller as BookingController / PaymentController / SepayController
    participant Service as BookingService / PaymentService
    participant Repository as JPA repositories / JdbcTemplate
    participant DB as PostgreSQL
    participant Worker as BookingEmailDispatcher
    participant SMTP as Configured SMTP provider
    Staff->>Security: POST /booking/{id}/contract/accept
    Security->>Controller: Require BOOKING_CONTRACT_MANAGE
    Controller->>Service: Validated reference, signer, signing date
    Service->>Repository: Lock booking, load contract and active booking passengers
    alt Cancelled booking, invalid signer/date/reference, contract notes supplied
        Service-->>Staff: Business error / validation 400; no contract content stored
    else Physical signature metadata valid
        Service->>DB: Store only contract reference, filing reference, signer and actual signed time
        Service->>Service: tryAutoConfirm
    end
    Note over Service,DB: Payment confirmation and verified SePay webhook also call tryAutoConfirm
    alt Contract not accepted or verified net collections below required deposit
        Service-->>Controller: Remain Draft; no confirmation email
    else Accepted contract and sufficient verified net collection
        Service->>Repository: confirmInternal locks booking, cruise and cabins
        Service->>DB: Check sale/lifecycle/capacity; set Confirmed, clear hold
        Service->>DB: Insert unique BOOKING_CONFIRMED outbox in same transaction
        Note over Service,DB: New guest accounts get hashed single-use setup token; existing accounts keep passwords
        DB-->>Controller: Commit booking/payment and outbox together
    end
    loop Enabled scheduler polls committed pending rows
        Worker->>DB: SELECT LIMIT 10 FOR UPDATE SKIP LOCKED
        Worker->>SMTP: Send booking, paid/outstanding amounts, dates and portal link
        alt SMTP succeeds
            Worker->>DB: sent_at and attempt count
        else SMTP fails
            Worker->>DB: Retain pending, retry after 5 minutes
        end
    end
```

SMTP runs after business commit and never gates the request on network availability.
Delivery is at least once: a worker crash after SMTP accepts mail but before commit can duplicate a message.
Scheduler defaults disabled; enable with deployment configuration. No email was sent during local validation.
No raw contract text/files or free-form contract notes are persisted.
Contract accept already accepted and booking already confirmed are idempotent and do not enqueue another confirmation.
Auto-confirmation failures now propagate so the enclosing contract/payment transaction rolls back rather than committing a confirmed booking without its outbox event. Payment integrations must retry or reconcile failed transactions.
`GET /payment/sepay/status/{paymentId}` reports `isBookingConfirmed` only when the booking itself is Confirmed; a confirmed payment on an unsigned-contract booking does not imply registration confirmation.

## Passenger account and website lookup

```mermaid
sequenceDiagram
    actor Passenger
    participant Security as Spring Security
    participant Controller as BookingAccountController / BookingController / ItineraryController
    participant Service as BookingEmailService / BookingService / PassengerItineraryService
    participant Repository as JPA repositories / JdbcTemplate
    participant DB as PostgreSQL
    Passenger->>Controller: POST /auth/booking-account/activate, emailed token + new password
    Controller->>Controller: Validate token length and password 12-72 characters
    Controller->>Service: activate
    Service->>DB: Lock unexpired, unused SHA-256 token hash row
    alt Invalid, expired, reused token or disabled account
        Service-->>Passenger: 400 / 403; no password change
    else Valid setup token for booking-provisioned account
        Service->>DB: Set encoded password; consume token; remove retained email link
        Service-->>Passenger: 200; sign in through existing /auth/login
    end
    Passenger->>Security: GET /booking or /booking/{id} + JWT + BOOKING_READ
    Security->>Controller: Authenticated permission check
    Controller->>Service: list / detail
    Service->>Repository: Scope to owner; staff uses existing permitted scope
    Repository->>DB: Read booking and financial evidence
    Service-->>Passenger: Paid total, outstanding balance, fullyPaid, installment projections and bookingPassenger IDs
    Passenger->>Controller: GET /itineraries/mine + JWT + BOOKING_READ
    Controller->>Service: mine(authenticated user ID)
    Service->>Repository: BookingPassenger linked to user, visible bookings and itineraries
    Repository->>DB: Fetch days/stops and activities by exact bookingPassenger IDs
    Service-->>Passenger: Per-booking trip guidance; activities filtered to matching cruise
```

Financial projection counts only positive confirmed Collect payments with confirmation time,
matching booking/currency and settlement evidence, less completed evidenced refunds.
Installment paid/outstanding values allocate net collections in sequence order; cancelled installments receive zero.
This is a read projection, not a rewrite of payment allocations. BookingStatus remains separate from fullyPaid.
Legacy guest lookup remains reference plus matching email/phone; existing authorization is preserved.
The frontend must implement `/activate-booking-account`, read the fragment token and POST it with a new password;
the backend never auto-signs in based on a contact email or idempotency key.
