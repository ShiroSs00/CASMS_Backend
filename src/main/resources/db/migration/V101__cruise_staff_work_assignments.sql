CREATE TABLE "StaffActivityQualification" (
    qualification_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES "User"(user_id),
    onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
    assigned_by uuid NOT NULL REFERENCES "User"(user_id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    ended_by uuid REFERENCES "User"(user_id),
    ended_at timestamptz,
    CHECK (num_nonnulls(onboard_activity_id, shore_excursion_id) = 1),
    CHECK ((ended_at IS NULL) = (ended_by IS NULL))
);

CREATE UNIQUE INDEX staff_qualification_active_onboard
    ON "StaffActivityQualification" (user_id, onboard_activity_id)
    WHERE ended_at IS NULL AND onboard_activity_id IS NOT NULL;
CREATE UNIQUE INDEX staff_qualification_active_shore
    ON "StaffActivityQualification" (user_id, shore_excursion_id)
    WHERE ended_at IS NULL AND shore_excursion_id IS NOT NULL;

CREATE TABLE "CruiseStaffDuty" (
    duty_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cruise_staff_id uuid NOT NULL REFERENCES "CruiseStaff"(cruise_staff_id),
    service_id uuid REFERENCES "Service"(service_id),
    onboard_activity_id uuid REFERENCES "OnboardActivity"(onboard_activity_id),
    shore_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id),
    assigned_by uuid NOT NULL REFERENCES "User"(user_id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    ended_by uuid REFERENCES "User"(user_id),
    ended_at timestamptz,
    CHECK (num_nonnulls(service_id, onboard_activity_id, shore_excursion_id) = 1),
    CHECK ((ended_at IS NULL) = (ended_by IS NULL))
);

CREATE UNIQUE INDEX cruise_staff_one_active_service
    ON "CruiseStaffDuty" (cruise_staff_id)
    WHERE ended_at IS NULL AND service_id IS NOT NULL;
CREATE UNIQUE INDEX cruise_staff_active_onboard_duty
    ON "CruiseStaffDuty" (cruise_staff_id, onboard_activity_id)
    WHERE ended_at IS NULL AND onboard_activity_id IS NOT NULL;
CREATE UNIQUE INDEX cruise_staff_active_shore_duty
    ON "CruiseStaffDuty" (cruise_staff_id, shore_excursion_id)
    WHERE ended_at IS NULL AND shore_excursion_id IS NOT NULL;

CREATE VIEW "CruiseStaffWorkWindow" AS
    SELECT cruise_id, 'SERVICE'::text AS kind, service_id AS resource_id,
        service_schedule_id AS window_id, service_id AS definition_id, service_name AS name,
        starts_at, ends_at
    FROM "ServiceSchedule" JOIN "Service" USING (service_id)
    WHERE "ServiceSchedule".status <> 'CANCELLED'
    UNION ALL
    SELECT cruise_id, 'ONBOARD', onboard_activity_id, onboard_activity_id,
        COALESCE(source_activity_id, onboard_activity_id), activity_name, start_time, end_time
    FROM "OnboardActivity"
    WHERE NOT catalog_entry AND status <> 'CANCELLED' AND start_time IS NOT NULL AND end_time IS NOT NULL
    UNION ALL
    SELECT i.cruise_id, 'SHORE', e.shore_excursion_id, e.shore_excursion_id,
        COALESCE(e.source_excursion_id, e.shore_excursion_id), e.excursion_name,
        e.start_time, e.planned_return_time
    FROM "ShoreExcursion" e JOIN "CruiseStop" s USING (cruise_stop_id)
    JOIN "Itinerary" i USING (itinerary_id)
    WHERE e.status <> 'CANCELLED' AND e.start_time IS NOT NULL AND e.planned_return_time IS NOT NULL;

CREATE VIEW "CruiseStaffDutyWindow" AS
    SELECT d.duty_id, cs.user_id, w.*
    FROM "CruiseStaffDuty" d JOIN "CruiseStaff" cs USING (cruise_staff_id)
    JOIN "Cruise" c ON c.cruise_id = cs.cruise_id
    JOIN "User" u ON u.user_id = cs.user_id
    JOIN "CruiseStaffWorkWindow" w ON w.cruise_id = cs.cruise_id AND
        ((w.kind = 'SERVICE' AND w.resource_id = d.service_id)
        OR (w.kind = 'ONBOARD' AND w.resource_id = d.onboard_activity_id)
        OR (w.kind = 'SHORE' AND w.resource_id = d.shore_excursion_id))
    WHERE d.ended_at IS NULL AND cs.status = 'ACTIVE' AND u.status = 'ACTIVE'
        AND c.status NOT IN ('COMPLETED', 'CANCELLED');

-- A schedule edit shares the employee lock with assignment writes. Separate windows of
-- one service duty represent the same responsibility and do not conflict with each other.
CREATE FUNCTION check_staff_work_window() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    changed_window uuid;
    changed_kind text;
    changed_cruise uuid;
    staff_user uuid;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'ServiceSchedule' THEN changed_window := NEW.service_schedule_id; changed_kind := 'SERVICE';
        WHEN 'OnboardActivity' THEN changed_window := NEW.onboard_activity_id; changed_kind := 'ONBOARD';
        WHEN 'ShoreExcursion' THEN changed_window := NEW.shore_excursion_id; changed_kind := 'SHORE';
    END CASE;
    SELECT cruise_id INTO changed_cruise FROM "CruiseStaffWorkWindow"
    WHERE kind = changed_kind AND window_id = changed_window;
    PERFORM 1 FROM "Cruise" WHERE cruise_id = changed_cruise FOR NO KEY UPDATE;
    FOR staff_user IN
        SELECT DISTINCT user_id FROM "CruiseStaffDutyWindow"
        WHERE kind = changed_kind AND window_id = changed_window ORDER BY user_id
    LOOP
        PERFORM 1 FROM "User" WHERE user_id = staff_user FOR NO KEY UPDATE;
        IF EXISTS (
            SELECT 1 FROM "CruiseStaffDutyWindow" changed
            JOIN "CruiseStaffDutyWindow" other
                ON other.user_id = changed.user_id AND other.duty_id <> changed.duty_id
                AND changed.starts_at < other.ends_at AND other.starts_at < changed.ends_at
            WHERE changed.kind = changed_kind AND changed.window_id = changed_window
                AND changed.user_id = staff_user
        ) THEN
            RAISE EXCEPTION 'STAFF_WORK_TIME_CONFLICT' USING ERRCODE = '23514',
                CONSTRAINT = 'staff_work_time_conflict';
        END IF;
    END LOOP;
    RETURN NEW;
END $$;

CREATE TRIGGER check_service_staff_work_window
    AFTER INSERT OR UPDATE OF starts_at, ends_at, status, service_id, cruise_id ON "ServiceSchedule"
    FOR EACH ROW EXECUTE FUNCTION check_staff_work_window();
CREATE TRIGGER check_onboard_staff_work_window
    AFTER UPDATE OF start_time, end_time, status, cruise_id ON "OnboardActivity"
    FOR EACH ROW EXECUTE FUNCTION check_staff_work_window();
CREATE TRIGGER check_shore_staff_work_window
    AFTER UPDATE OF start_time, planned_return_time, status, cruise_stop_id ON "ShoreExcursion"
    FOR EACH ROW EXECUTE FUNCTION check_staff_work_window();
