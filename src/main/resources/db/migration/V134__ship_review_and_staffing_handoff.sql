ALTER TABLE "StaffingRequest"
    ADD COLUMN ship_review_workflow boolean NOT NULL DEFAULT false,
    ADD COLUMN ship_review_fingerprint text,
    ADD COLUMN hr_approved_by uuid REFERENCES "User"(user_id),
    DROP CONSTRAINT "StaffingRequest_status_check",
    DROP CONSTRAINT "StaffingRequest_check";

ALTER TABLE "StaffingRequest" ADD CONSTRAINT staffing_request_status_check CHECK (status IN (
    'PENDING_SHIP_REVIEW','SENT_TO_HR','HR_SUBMITTED','SHIP_CONFIRMED',
    'HR_APPROVED','PLANNER_CONFIRMED','OPS_APPROVED','RETURNED','CANCELLED'
)), ADD CONSTRAINT staffing_request_ops_approval_check CHECK (
    status <> 'OPS_APPROVED' OR (
        hr_submitted_by IS NOT NULL AND ship_confirmed_by IS NOT NULL AND ops_approved_by IS NOT NULL
        AND ((ship_review_workflow AND hr_approved_by IS NOT NULL)
            OR (NOT ship_review_workflow AND planner_confirmed_by IS NOT NULL))
    )
);

CREATE VIEW "StaffingShipModel" AS
SELECT c.cruise_id, c.ship_id, m.ship_model_id
FROM "Cruise" c
JOIN LATERAL (
    SELECT ship_model_id FROM "ShipModel" m
    WHERE m.ship_id=c.ship_id AND m.status='READY'
    ORDER BY (m.model_key IS NOT NULL) DESC, m.created_at DESC, m.ship_model_id DESC
    LIMIT 1
) m ON true;

CREATE VIEW "StaffingServingArea" AS
SELECT m.cruise_id, m.ship_id, a.ship_model_object_id AS area_id, a.display_name AS area_name
FROM "StaffingShipModel" m
JOIN "ShipModelObject" a USING (ship_model_id)
WHERE a.object_type='FACILITY' AND a.area_usage='SERVICE';

CREATE VIEW "StaffingAreaCrew" AS
SELECT a.cruise_id, a.area_id, cs.user_id
FROM "StaffingServingArea" a
JOIN "Cruise" c USING (cruise_id)
JOIN "AreaStaffAssignment" asa ON asa.area_object_id=a.area_id AND asa.enabled
JOIN "CruiseStaff" cs ON cs.cruise_staff_id=asa.cruise_staff_id
    AND cs.cruise_id=a.cruise_id AND cs.status='ACTIVE'
JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
WHERE asa.starts_at < c.return_datetime
    AND (asa.ends_at IS NULL OR asa.ends_at > c.departure_datetime);

CREATE VIEW "StaffingAreaPos" AS
SELECT a.cruise_id, a.area_id, t.terminal_id, t.terminal_name
FROM "StaffingServingArea" a
JOIN "PosTerminalArea" ta ON ta.area_object_id=a.area_id AND ta.enabled
JOIN "PosTerminal" t ON t.terminal_id=ta.terminal_id AND t.ship_id=a.ship_id AND t.enabled;

CREATE VIEW "StaffingShipInspection" AS
SELECT c.cruise_id, m.ship_model_id,
    s.status IN ('READY','IN_SERVICE') AND EXISTS (
        SELECT 1 FROM "Cabin" b WHERE b.ship_id=s.ship_id AND b.status IN ('AVAILABLE','OCCUPIED')
    ) AS ship_ready,
    NOT EXISTS (
        SELECT 1 FROM "StaffingServingArea" a WHERE a.cruise_id=c.cruise_id
        AND NOT EXISTS (
            SELECT 1 FROM "StaffingAreaPos" p WHERE p.cruise_id=a.cruise_id AND p.area_id=a.area_id
        )
    ) AS pos_covered,
    md5(jsonb_build_array(m.ship_model_id, ARRAY(
        SELECT jsonb_build_array(a.area_id, p.terminal_id)
        FROM "StaffingServingArea" a
        LEFT JOIN "StaffingAreaPos" p USING (cruise_id, area_id)
        WHERE a.cruise_id=c.cruise_id ORDER BY a.area_id, p.terminal_id
    ))::text) AS fingerprint
FROM "Cruise" c
LEFT JOIN "Ship" s USING (ship_id)
LEFT JOIN "StaffingShipModel" m USING (cruise_id);

CREATE OR REPLACE FUNCTION reset_staffing_approval(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "StaffingRequest"
    SET status='SENT_TO_HR', revision=revision+1, updated_at=now(),
        hr_submitted_by=NULL, ship_confirmed_by=NULL, planner_confirmed_by=NULL,
        ops_approved_by=NULL, hr_approved_by=NULL
    WHERE cruise_id=affected_cruise
        AND status IN ('HR_SUBMITTED','SHIP_CONFIRMED','HR_APPROVED','PLANNER_CONFIRMED','OPS_APPROVED');
    SELECT invalidate_staffing_preparation(affected_cruise);
$$;

CREATE FUNCTION invalidate_staffing_on_area_crew_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE affected_cruise uuid;
BEGIN
    FOR affected_cruise IN
        SELECT DISTINCT cruise_id FROM "CruiseStaff"
        WHERE (TG_OP <> 'INSERT' AND cruise_staff_id=OLD.cruise_staff_id)
           OR (TG_OP <> 'DELETE' AND cruise_staff_id=NEW.cruise_staff_id)
        ORDER BY cruise_id
    LOOP
        -- This also serializes area edits against workflow review.
        PERFORM 1 FROM "Cruise" WHERE cruise_id=affected_cruise FOR NO KEY UPDATE;
        IF EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=affected_cruise
                AND ship_review_workflow AND status IN ('HR_SUBMITTED','SHIP_CONFIRMED','HR_APPROVED','OPS_APPROVED')) THEN
            PERFORM reset_staffing_approval(affected_cruise);
        END IF;
    END LOOP;
    RETURN NULL;
END $$;

CREATE TRIGGER invalidate_staffing_on_area_crew_change
    AFTER INSERT OR DELETE OR UPDATE OF enabled, starts_at, ends_at, area_object_id, cruise_staff_id
    ON "AreaStaffAssignment" FOR EACH ROW EXECUTE FUNCTION invalidate_staffing_on_area_crew_change();
