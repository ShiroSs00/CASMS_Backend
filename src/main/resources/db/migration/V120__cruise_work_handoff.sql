CREATE TABLE "CruiseWorkHandoff" (
    cruise_id UUID NOT NULL REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    work_group VARCHAR(8) NOT NULL CHECK (work_group IN ('ASM', 'SHIP')),
    requested_by UUID NOT NULL REFERENCES "User"(user_id),
    assignee_id UUID REFERENCES "User"(user_id),
    request_text VARCHAR(2000) NOT NULL,
    response_text VARCHAR(2000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','RETURNED')),
    revision BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cruise_id, work_group)
);
