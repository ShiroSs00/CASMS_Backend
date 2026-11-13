CREATE TABLE "StaffAssignmentEmailTemplate" (
    version bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject varchar(180) NOT NULL,
    heading varchar(180) NOT NULL,
    introduction varchar(4000) NOT NULL,
    image_url varchar(2048),
    button_label varchar(80),
    button_url varchar(2048),
    published_by uuid NOT NULL REFERENCES "User"(user_id),
    published_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((button_label IS NULL) = (button_url IS NULL))
);

CREATE TABLE "StaffAssignmentEmailOutbox" (
    outbox_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id uuid UNIQUE REFERENCES "CruiseStaff"(cruise_staff_id) ON DELETE SET NULL,
    template_version bigint REFERENCES "StaffAssignmentEmailTemplate"(version),
    recipient varchar(255),
    subject varchar(180) NOT NULL,
    body_text text NOT NULL,
    body_html text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    sent_at timestamptz,
    last_retry_by uuid REFERENCES "User"(user_id),
    last_retry_at timestamptz
);

CREATE INDEX staff_assignment_email_pending
    ON "StaffAssignmentEmailOutbox" (next_attempt_at, outbox_id)
    WHERE sent_at IS NULL AND recipient IS NOT NULL AND attempts < 5;
