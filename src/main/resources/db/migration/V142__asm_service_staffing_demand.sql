-- ASM-owned service & activity staffing demand + service-level HR assignments synced with POS areas.
ALTER TABLE "Service"
    ADD COLUMN IF NOT EXISTS default_required_staff integer NOT NULL DEFAULT 1
        CHECK (default_required_staff >= 1);

ALTER TABLE "ServiceSchedule"
    ADD COLUMN IF NOT EXISTS required_staff_count integer NOT NULL DEFAULT 1
        CHECK (required_staff_count >= 1);

ALTER TABLE "OnboardActivity"
    ADD COLUMN IF NOT EXISTS required_staff_count integer NOT NULL DEFAULT 1
        CHECK (required_staff_count >= 1);

ALTER TABLE "ShoreExcursion"
    ADD COLUMN IF NOT EXISTS required_staff_count integer NOT NULL DEFAULT 1
        CHECK (required_staff_count >= 1);

CREATE TABLE IF NOT EXISTS "StaffingServiceAssignment" (
    request_id uuid NOT NULL REFERENCES "StaffingRequest"(request_id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    cruise_staff_id uuid NOT NULL REFERENCES "CruiseStaff"(cruise_staff_id) ON DELETE CASCADE,
    PRIMARY KEY (request_id, service_id, cruise_staff_id),
    UNIQUE (request_id, cruise_staff_id),
    FOREIGN KEY (request_id, service_id) REFERENCES "StaffingServicePlacement"(request_id, service_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS staffing_service_assignment_req_svc_idx
    ON "StaffingServiceAssignment"(request_id, service_id);
