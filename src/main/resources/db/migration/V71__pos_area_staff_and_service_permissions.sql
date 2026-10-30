-- Explicit access assignments; never infer employee access from a global role alone.
CREATE TABLE "AreaStaffAssignment" (
    assignment_id uuid PRIMARY KEY,
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    cruise_staff_id uuid NOT NULL REFERENCES "CruiseStaff"(cruise_staff_id),
    enabled boolean NOT NULL DEFAULT true,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz,
    assigned_by_user_id uuid NOT NULL REFERENCES "User"(user_id),
    assigned_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE(area_object_id,cruise_staff_id),
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX idx_area_staff_crew ON "AreaStaffAssignment"(cruise_staff_id);

-- Retain the original terminal area as its default, and support explicit additional areas.
CREATE TABLE "PosTerminalArea" (
    terminal_id uuid NOT NULL REFERENCES "PosTerminal"(terminal_id),
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(terminal_id,area_object_id)
);
INSERT INTO "PosTerminalArea" (terminal_id,area_object_id)
SELECT terminal_id,area_object_id FROM "PosTerminal";

CREATE TABLE "PosAreaServicePermission" (
    terminal_id uuid NOT NULL,
    area_object_id uuid NOT NULL,
    service_id uuid NOT NULL,
    permissions text[] NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(terminal_id,area_object_id,service_id),
    FOREIGN KEY(terminal_id,area_object_id) REFERENCES "PosTerminalArea"(terminal_id,area_object_id),
    FOREIGN KEY(area_object_id,service_id) REFERENCES "AreaServiceAssignment"(area_object_id,service_id) ON DELETE CASCADE,
    CHECK (permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN',
        'SERVICE_REQUEST_EXECUTE','SERVICE_SALE']::text[])
);
-- Preserve existing device/service configuration. New relationships require explicit policy setup.
INSERT INTO "PosAreaServicePermission" (terminal_id,area_object_id,service_id,permissions,enabled)
SELECT t.terminal_id,t.area_object_id,a.service_id,a.permissions,a.enabled
FROM "PosTerminal" t JOIN "AreaServiceAssignment" a ON a.area_object_id=t.area_object_id;

ALTER TABLE "PosSession" ADD COLUMN area_staff_assignment_id uuid REFERENCES "AreaStaffAssignment"(assignment_id);
-- Old sessions have no employee-area/cruise proof and cannot be grandfathered in.
UPDATE "PosSession" SET revoked_at=COALESCE(revoked_at,now());
CREATE INDEX idx_pos_session_staff_assignment ON "PosSession"(area_staff_assignment_id);
