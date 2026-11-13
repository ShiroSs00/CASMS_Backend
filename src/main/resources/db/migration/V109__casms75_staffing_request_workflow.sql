-- Additive: staffing demand and approval trail attached to a cruise.
CREATE TABLE "StaffingRequest" (
  request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id),
  schedule_revision bigint NOT NULL CHECK (schedule_revision >= 0),
  status varchar(32) NOT NULL DEFAULT 'SENT_TO_HR' CHECK (status IN
    ('SENT_TO_HR','HR_SUBMITTED','SHIP_CONFIRMED','PLANNER_CONFIRMED','OPS_APPROVED','RETURNED','CANCELLED')),
  revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
  requested_by uuid NOT NULL REFERENCES "User"(user_id),
  requested_at timestamptz NOT NULL DEFAULT now(),
  hr_submitted_by uuid REFERENCES "User"(user_id),
  ship_confirmed_by uuid REFERENCES "User"(user_id),
  planner_confirmed_by uuid REFERENCES "User"(user_id),
  ops_approved_by uuid REFERENCES "User"(user_id),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status <> 'OPS_APPROVED' OR (hr_submitted_by IS NOT NULL AND ship_confirmed_by IS NOT NULL
          AND planner_confirmed_by IS NOT NULL AND ops_approved_by IS NOT NULL))
);
CREATE UNIQUE INDEX staffing_one_open_request_per_cruise
  ON "StaffingRequest"(cruise_id) WHERE status NOT IN ('CANCELLED','RETURNED');
CREATE INDEX staffing_request_status_idx ON "StaffingRequest"(status,requested_at);
CREATE TABLE "StaffingRequirement" (
  requirement_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES "StaffingRequest"(request_id) ON DELETE CASCADE,
  service_id uuid NOT NULL REFERENCES "Service"(service_id),
  required_count int NOT NULL CHECK (required_count > 0),
  UNIQUE(request_id,service_id)
);
CREATE TABLE "StaffingDecision" (
  decision_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES "StaffingRequest"(request_id),
  revision bigint NOT NULL,
  action varchar(32) NOT NULL,
  actor_id uuid NOT NULL REFERENCES "User"(user_id),
  comment text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX staffing_decision_request_idx ON "StaffingDecision"(request_id,created_at);

CREATE FUNCTION invalidate_staffing_preparation(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "CruisePreparationSection"
    SET status='RETURNED', revision=revision+1, updated_at=now(), returned_at=now(),
        returned_by_user_id=NULL, return_reason='Yêu cầu nhân sự đã thay đổi.'
    WHERE cruise_id=affected_cruise AND section_code='SAFETY_CREW'
        AND status IN ('SUBMITTED','CONFIRMED');
$$;

-- Membership and duty edits restart the current review round. Preserve the decision history.
CREATE FUNCTION reset_staffing_approval(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "StaffingRequest"
    SET status='SENT_TO_HR', revision=revision+1, updated_at=now(),
        hr_submitted_by=NULL, ship_confirmed_by=NULL, planner_confirmed_by=NULL, ops_approved_by=NULL
    WHERE cruise_id=affected_cruise
        AND status IN ('HR_SUBMITTED','SHIP_CONFIRMED','PLANNER_CONFIRMED','OPS_APPROVED');
    SELECT invalidate_staffing_preparation(affected_cruise);
$$;

CREATE FUNCTION invalidate_staffing_on_duty_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE affected_cruise uuid;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        SELECT cruise_id INTO affected_cruise FROM "CruiseStaff"
        WHERE cruise_staff_id=OLD.cruise_staff_id;
        PERFORM reset_staffing_approval(affected_cruise);
    END IF;
    IF TG_OP='INSERT' OR (TG_OP='UPDATE' AND NEW.cruise_staff_id IS DISTINCT FROM OLD.cruise_staff_id) THEN
        SELECT cruise_id INTO affected_cruise FROM "CruiseStaff"
        WHERE cruise_staff_id=NEW.cruise_staff_id;
        PERFORM reset_staffing_approval(affected_cruise);
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_duty_change
    AFTER INSERT OR DELETE OR UPDATE OF ended_at, cruise_staff_id, service_id, onboard_activity_id, shore_excursion_id
    ON "CruiseStaffDuty" FOR EACH ROW
    EXECUTE FUNCTION invalidate_staffing_on_duty_change();

CREATE FUNCTION invalidate_staffing_on_membership_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM reset_staffing_approval(OLD.cruise_id);
    END IF;
    IF TG_OP='INSERT' OR (TG_OP='UPDATE' AND NEW.cruise_id IS DISTINCT FROM OLD.cruise_id) THEN
        PERFORM reset_staffing_approval(NEW.cruise_id);
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_membership_insert_delete
    AFTER INSERT OR DELETE ON "CruiseStaff" FOR EACH ROW
    EXECUTE FUNCTION invalidate_staffing_on_membership_change();
CREATE TRIGGER invalidate_staffing_on_membership_update
    AFTER UPDATE ON "CruiseStaff" FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status
        OR OLD.role_in_cruise IS DISTINCT FROM NEW.role_in_cruise
        OR OLD.cruise_id IS DISTINCT FROM NEW.cruise_id
        OR OLD.user_id IS DISTINCT FROM NEW.user_id)
    EXECUTE FUNCTION invalidate_staffing_on_membership_change();

-- The coordinator must submit fresh demand after a planning change. The original
-- schedule_revision records the Cruise revision checked at submission.
CREATE FUNCTION invalidate_staffing_plan(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "StaffingRequest"
    SET status='CANCELLED', revision=revision+1, updated_at=now()
    WHERE cruise_id=affected_cruise AND status NOT IN ('CANCELLED','RETURNED');
    SELECT invalidate_staffing_preparation(affected_cruise);
$$;

CREATE FUNCTION invalidate_staffing_on_cruise_plan_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM invalidate_staffing_plan(NEW.cruise_id);
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_cruise_plan_change
    AFTER UPDATE ON "Cruise" FOR EACH ROW
    WHEN (OLD.departure_datetime IS DISTINCT FROM NEW.departure_datetime
        OR OLD.return_datetime IS DISTINCT FROM NEW.return_datetime
        OR OLD.ship_id IS DISTINCT FROM NEW.ship_id
        OR OLD.preparation_coordinator_id IS DISTINCT FROM NEW.preparation_coordinator_id
        OR (OLD.status IS DISTINCT FROM NEW.status AND NEW.status IN ('COMPLETED','CANCELLED')))
    EXECUTE FUNCTION invalidate_staffing_on_cruise_plan_change();

-- Activation and role changes must not revive a prior approval on reactivation.
CREATE FUNCTION invalidate_staffing_on_user_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE affected_user uuid;
DECLARE affected_cruise uuid;
BEGIN
    IF TG_OP='DELETE' THEN
        affected_user := OLD.user_id;
    ELSE
        affected_user := NEW.user_id;
    END IF;

    FOR affected_cruise IN
        SELECT cruise_id FROM "CruiseStaff" WHERE user_id=affected_user AND status='ACTIVE'
        UNION
        SELECT cruise_id FROM "StaffingRequest"
        WHERE affected_user IN (hr_submitted_by, ship_confirmed_by, planner_confirmed_by, ops_approved_by)
        ORDER BY cruise_id
    LOOP
        PERFORM reset_staffing_approval(affected_cruise);
    END LOOP;
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_user_status
    AFTER UPDATE OF status ON "User" FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION invalidate_staffing_on_user_change();

CREATE TRIGGER invalidate_staffing_on_user_role
    AFTER INSERT OR DELETE ON "UserRole" FOR EACH ROW
    EXECUTE FUNCTION invalidate_staffing_on_user_change();

CREATE FUNCTION invalidate_staffing_on_service_status() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE affected_cruise uuid;
BEGIN
    FOR affected_cruise IN
        SELECT DISTINCT req.cruise_id FROM "StaffingRequest" req
        JOIN "StaffingRequirement" r USING (request_id)
        WHERE r.service_id=NEW.service_id AND req.status NOT IN ('CANCELLED','RETURNED')
        ORDER BY req.cruise_id
    LOOP
        PERFORM reset_staffing_approval(affected_cruise);
    END LOOP;
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_service_status
    AFTER UPDATE OF status ON "Service" FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION invalidate_staffing_on_service_status();
