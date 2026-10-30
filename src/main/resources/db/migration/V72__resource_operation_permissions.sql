-- Canonical operation policies belong to catalogue resources, not imported model metadata.
CREATE TABLE "ServiceOperationPolicy" (
    service_id uuid PRIMARY KEY REFERENCES "Service"(service_id) ON DELETE CASCADE,
    permissions text[] NOT NULL DEFAULT '{}',
    updated_by_user_id uuid REFERENCES "User"(user_id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN','SERVICE_REQUEST_EXECUTE','SERVICE_SALE']::text[])
);
-- Preserve old grants only through their existing area and device restrictions.
INSERT INTO "ServiceOperationPolicy" (service_id,permissions)
SELECT a.service_id,array_agg(DISTINCT p.permission ORDER BY p.permission)
FROM "AreaServiceAssignment" a CROSS JOIN LATERAL unnest(a.permissions) p(permission)
GROUP BY a.service_id;

CREATE TABLE "ActivityOperationPolicy" (
    activity_id uuid PRIMARY KEY REFERENCES "OnboardActivity"(onboard_activity_id) ON DELETE CASCADE,
    permissions text[] NOT NULL DEFAULT '{}',
    updated_by_user_id uuid REFERENCES "User"(user_id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (permissions <@ ARRAY['ACTIVITY_READ','ACTIVITY_REGISTRATION','ACTIVITY_ATTENDANCE','ACTIVITY_LIFECYCLE']::text[])
);
ALTER TABLE "AreaServiceAssignment" ADD COLUMN inherit_permissions boolean NOT NULL DEFAULT false;
ALTER TABLE "PosAreaServicePermission" ADD COLUMN inherit_permissions boolean NOT NULL DEFAULT false;
-- A missing override will inherit for new links. Preserve every existing denial first.
INSERT INTO "PosAreaServicePermission" (terminal_id,area_object_id,service_id,enabled)
SELECT t.terminal_id,t.area_object_id,a.service_id,false
FROM "PosTerminalArea" t JOIN "AreaServiceAssignment" a ON a.area_object_id=t.area_object_id
ON CONFLICT DO NOTHING;

CREATE TABLE "AreaActivityAssignment" (
    area_object_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    activity_id uuid NOT NULL REFERENCES "OnboardActivity"(onboard_activity_id) ON DELETE CASCADE,
    permissions text[] NOT NULL DEFAULT '{}',
    inherit_permissions boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(area_object_id,activity_id),
    CHECK (permissions <@ ARRAY['ACTIVITY_READ','ACTIVITY_REGISTRATION','ACTIVITY_ATTENDANCE','ACTIVITY_LIFECYCLE']::text[])
);
CREATE TABLE "PosAreaActivityPermission" (
    terminal_id uuid NOT NULL,
    area_object_id uuid NOT NULL,
    activity_id uuid NOT NULL,
    permissions text[] NOT NULL DEFAULT '{}',
    inherit_permissions boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(terminal_id,area_object_id,activity_id),
    FOREIGN KEY(terminal_id,area_object_id) REFERENCES "PosTerminalArea"(terminal_id,area_object_id),
    FOREIGN KEY(area_object_id,activity_id) REFERENCES "AreaActivityAssignment"(area_object_id,activity_id) ON DELETE CASCADE,
    CHECK (permissions <@ ARRAY['ACTIVITY_READ','ACTIVITY_REGISTRATION','ACTIVITY_ATTENDANCE','ACTIVITY_LIFECYCLE']::text[])
);
ALTER TABLE "PosTerminal" DROP CONSTRAINT "PosTerminal_permissions_check";
ALTER TABLE "PosTerminal" ADD CONSTRAINT "PosTerminal_permissions_check" CHECK (
    permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN','SERVICE_REQUEST_EXECUTE','SERVICE_SALE',
        'ACTIVITY_READ','ACTIVITY_REGISTRATION','ACTIVITY_ATTENDANCE','ACTIVITY_LIFECYCLE']::text[]);
