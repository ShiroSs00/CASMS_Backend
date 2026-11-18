CREATE TABLE booking_cancellation_case (
    case_id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES "Booking"(booking_id),
    requested_by uuid NOT NULL REFERENCES "User"(user_id),
    idempotency_key varchar(128) NOT NULL,
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    scenario varchar(32) NOT NULL CHECK (scenario IN ('CUSTOMER', 'COMPANY', 'OPERATIONAL_DISRUPTION')),
    issue_id uuid REFERENCES "OperationalIssue"(issue_id),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING_OPS','PENDING_FINANCE','COMPLETED','REJECTED','WITHDRAWN')),
    requested_at timestamptz NOT NULL,
    departure_at timestamptz NOT NULL,
    verified_by uuid REFERENCES "User"(user_id),
    verified_at timestamptz,
    decided_by uuid REFERENCES "User"(user_id),
    decided_at timestamptz,
    decision_reason varchar(1000),
    revision bigint NOT NULL DEFAULT 0,
    UNIQUE (booking_id, requested_by, idempotency_key),
    CHECK ((scenario = 'OPERATIONAL_DISRUPTION') = (issue_id IS NOT NULL))
);

CREATE UNIQUE INDEX cancellation_one_active_case
    ON booking_cancellation_case (booking_id)
    WHERE status IN ('PENDING_OPS','PENDING_FINANCE');
CREATE INDEX cancellation_case_booking_history
    ON booking_cancellation_case (booking_id, requested_at DESC);
CREATE INDEX cancellation_case_review_queue
    ON booking_cancellation_case (status, requested_at DESC);
