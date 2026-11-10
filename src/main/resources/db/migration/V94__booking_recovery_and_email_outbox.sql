-- Legacy guest duplicates cannot safely be guessed or discarded. Fail with an actionable error.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM "IdempotencyRecord" WHERE actor_id IS NULL
               AND operation = 'CREATE_BOOKING' GROUP BY idempotency_key HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate guest booking idempotency keys require manual reconciliation before V94';
    END IF;
END $$;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM "User" WHERE email IS NOT NULL AND btrim(email) <> ''
               GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate normalized user emails require manual reconciliation before V94';
    END IF;
END $$;
CREATE UNIQUE INDEX uq_user_normalized_email ON "User" (lower(btrim(email)))
    WHERE email IS NOT NULL AND btrim(email) <> '';
CREATE UNIQUE INDEX uq_guest_booking_idempotency
    ON "IdempotencyRecord" (operation, idempotency_key)
    WHERE actor_id IS NULL AND operation = 'CREATE_BOOKING';

CREATE TABLE "BookingAccountSetup" (
    user_id uuid PRIMARY KEY REFERENCES "User"(user_id),
    token_hash varchar(64) UNIQUE,
    expires_at timestamptz,
    activated_at timestamptz
);
CREATE TABLE "BookingEmailOutbox" (
    event_key varchar(255) PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES "Booking"(booking_id),
    recipient varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    body text NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_booking_email_pending ON "BookingEmailOutbox" (next_attempt_at) WHERE sent_at IS NULL;
