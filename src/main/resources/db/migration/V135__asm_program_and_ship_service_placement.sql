ALTER TABLE "StaffingRequest"
    ADD COLUMN asm_program_workflow boolean NOT NULL DEFAULT false,
    ADD COLUMN service_review_fingerprint text,
    DROP CONSTRAINT staffing_request_status_check;

ALTER TABLE "StaffingRequest" ADD CONSTRAINT staffing_request_status_check CHECK (status IN (
    'PENDING_ASM','PENDING_SHIP_REVIEW','SENT_TO_HR','HR_SUBMITTED','SHIP_CONFIRMED',
    'HR_APPROVED','PLANNER_CONFIRMED','OPS_APPROVED','RETURNED','CANCELLED'
));

-- A serving window scoped to a port belongs to the shore program and has no ship POS placement.
CREATE VIEW "StaffingPlannedOnboardService" AS
SELECT DISTINCT w.cruise_id, s.service_id, s.service_name
FROM "ServiceSchedule" w JOIN "Service" s USING (service_id)
WHERE w.cruise_id IS NOT NULL AND w.cruise_stop_id IS NULL AND w.status <> 'CANCELLED';

CREATE VIEW "StaffingPlannedServicePlacement" AS
SELECT planned.cruise_id, planned.service_id, a.area_id, pos.terminal_id
FROM "StaffingPlannedOnboardService" planned
JOIN "Service" s ON s.service_id=planned.service_id AND s.status='ACTIVE'
JOIN "StaffingServingArea" a ON a.cruise_id=planned.cruise_id
JOIN "ShipModelObject" o ON o.ship_model_object_id=a.area_id AND o.cabin_id IS NULL
JOIN "Deck" deck ON deck.deck_id=o.deck_id AND deck.ship_id=a.ship_id
JOIN "DeckServiceAssignment" d ON d.deck_id=o.deck_id AND d.service_id=planned.service_id
JOIN "AreaServiceAssignment" assignment ON assignment.area_object_id=a.area_id
    AND assignment.service_id=planned.service_id AND assignment.enabled
JOIN "StaffingAreaPos" pos ON pos.cruise_id=a.cruise_id AND pos.area_id=a.area_id
JOIN "PosTerminal" terminal ON terminal.terminal_id=pos.terminal_id
JOIN "ServiceOperationPolicy" policy ON policy.service_id=planned.service_id
LEFT JOIN "PosAreaServicePermission" permission ON permission.terminal_id=pos.terminal_id
    AND permission.area_object_id=a.area_id AND permission.service_id=planned.service_id
WHERE 'SERVICE_READ'=ANY(policy.permissions)
    AND 'SERVICE_READ'=ANY(terminal.permissions)
    AND (assignment.inherit_permissions OR 'SERVICE_READ'=ANY(assignment.permissions))
    AND (permission.service_id IS NULL OR permission.enabled
        AND (permission.inherit_permissions OR 'SERVICE_READ'=ANY(permission.permissions)));

CREATE VIEW "StaffingProgramInspection" AS
SELECT c.cruise_id,
    EXISTS (
        SELECT 1 FROM "CruiseWorkHandoff" w
        WHERE w.cruise_id=c.cruise_id AND w.work_group='ASM' AND w.status='COMPLETED'
    ) AS asm_completed,
    NOT EXISTS (
        SELECT 1 FROM "StaffingPlannedOnboardService" service WHERE service.cruise_id=c.cruise_id
        AND NOT EXISTS (
            SELECT 1 FROM "StaffingPlannedServicePlacement" p
            WHERE p.cruise_id=c.cruise_id AND p.service_id=service.service_id
        )
    ) AS services_covered,
    md5(ARRAY(
        SELECT jsonb_build_array(s.service_id, p.area_id, p.terminal_id)
        FROM "StaffingPlannedOnboardService" s
        LEFT JOIN "StaffingPlannedServicePlacement" p USING (cruise_id, service_id)
        WHERE s.cruise_id=c.cruise_id ORDER BY s.service_id, p.area_id, p.terminal_id
    )::text) AS fingerprint
FROM "Cruise" c;
