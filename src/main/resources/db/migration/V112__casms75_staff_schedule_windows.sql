-- Serialize crew assignment and timetable changes on the employee row.
CREATE UNIQUE INDEX uq_cruise_staff_one_active_per_cruise_user
    ON "CruiseStaff"(cruise_id, user_id) WHERE status = 'ACTIVE';
CREATE INDEX cruise_staff_active_user_lookup
    ON "CruiseStaff"(user_id) WHERE status = 'ACTIVE';

CREATE FUNCTION check_cruise_staff_window(employee uuid, cruise uuid,
    departure timestamptz, arrival timestamptz) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM "User" WHERE user_id = employee FOR UPDATE;
    IF EXISTS (
        SELECT 1 FROM "CruiseStaff" cs JOIN "Cruise" c USING (cruise_id)
        WHERE cs.user_id = employee AND cs.status = 'ACTIVE' AND cs.cruise_id <> cruise
          AND c.status NOT IN ('COMPLETED','CANCELLED')
          AND c.departure_datetime < arrival AND c.return_datetime > departure
    ) THEN
        RAISE EXCEPTION 'SHIP_STAFF_SCHEDULE_CONFLICT' USING ERRCODE = '23514';
    END IF;
END $$;

CREATE FUNCTION enforce_cruise_staff_window() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE trip "Cruise"%ROWTYPE;
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        SELECT * INTO trip FROM "Cruise" WHERE cruise_id = NEW.cruise_id FOR SHARE;
        PERFORM check_cruise_staff_window(NEW.user_id, NEW.cruise_id,
            trip.departure_datetime, trip.return_datetime);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER enforce_cruise_staff_window
    BEFORE INSERT OR UPDATE OF status, cruise_id, user_id ON "CruiseStaff"
    FOR EACH ROW EXECUTE FUNCTION enforce_cruise_staff_window();

CREATE FUNCTION enforce_cruise_schedule_staff_windows() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE employee uuid;
BEGIN
    IF NEW.status NOT IN ('COMPLETED','CANCELLED') THEN
        FOR employee IN SELECT user_id FROM "CruiseStaff"
            WHERE cruise_id = NEW.cruise_id AND status = 'ACTIVE' ORDER BY user_id
        LOOP
            PERFORM check_cruise_staff_window(employee, NEW.cruise_id,
                NEW.departure_datetime, NEW.return_datetime);
        END LOOP;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER enforce_cruise_schedule_staff_windows
    BEFORE UPDATE OF departure_datetime, return_datetime, status ON "Cruise"
    FOR EACH ROW WHEN (OLD.departure_datetime IS DISTINCT FROM NEW.departure_datetime
        OR OLD.return_datetime IS DISTINCT FROM NEW.return_datetime
        OR OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION enforce_cruise_schedule_staff_windows();

DROP INDEX uq_cruise_staff_active_user;
