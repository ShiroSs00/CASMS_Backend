ALTER TABLE "CruiseStaff" ADD COLUMN removal_reason varchar(500);

UPDATE "CruiseStaff" cs
SET status = c.status::text,
    ended_at = COALESCE(cs.ended_at, CASE WHEN c.status = 'COMPLETED' THEN c.actual_return_datetime END, now())
FROM "Cruise" c
WHERE c.cruise_id = cs.cruise_id AND cs.status = 'ACTIVE'
    AND c.status IN ('COMPLETED', 'CANCELLED');

ALTER TABLE "CruiseStaff" DROP CONSTRAINT fk_cruise_staff_cruise;
ALTER TABLE "CruiseStaff" ADD CONSTRAINT fk_cruise_staff_cruise
    FOREIGN KEY (cruise_id) REFERENCES "Cruise"(cruise_id);
ALTER TABLE "CruiseStaff" DROP CONSTRAINT fk_cruise_staff_user;
ALTER TABLE "CruiseStaff" ADD CONSTRAINT fk_cruise_staff_user
    FOREIGN KEY (user_id) REFERENCES "User"(user_id);

CREATE FUNCTION ensure_active_cruise_staff_has_open_cruise() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cruise_status text;
BEGIN
    IF NEW.status <> 'ACTIVE' THEN RETURN NEW; END IF;
    SELECT c.status::text INTO cruise_status FROM "Cruise" c
        WHERE c.cruise_id = NEW.cruise_id FOR SHARE;
    IF cruise_status IN ('COMPLETED', 'CANCELLED') THEN
        RAISE EXCEPTION 'CRUISE_STAFF_CRUISE_CLOSED' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER enforce_cruise_staff_lifecycle
    BEFORE INSERT OR UPDATE OF status, cruise_id ON "CruiseStaff"
    FOR EACH ROW EXECUTE FUNCTION ensure_active_cruise_staff_has_open_cruise();

CREATE FUNCTION finish_cruise_staff_assignments() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE "CruiseStaff" SET status = NEW.status::text,
        ended_at = COALESCE(CASE WHEN NEW.status = 'COMPLETED' THEN NEW.actual_return_datetime END, now())
    WHERE cruise_id = NEW.cruise_id AND status = 'ACTIVE';
    RETURN NEW;
END $$;

CREATE TRIGGER finish_cruise_staff_after_cruise_closes
    AFTER UPDATE OF status ON "Cruise" FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status AND NEW.status IN ('COMPLETED', 'CANCELLED'))
    EXECUTE FUNCTION finish_cruise_staff_assignments();
