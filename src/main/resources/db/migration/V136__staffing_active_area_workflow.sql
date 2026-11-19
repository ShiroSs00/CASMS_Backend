ALTER TABLE "StaffingRequest"
    ADD COLUMN workflow_version varchar(8) NOT NULL DEFAULT 'LEGACY'
        CHECK (workflow_version IN ('LEGACY','V2')),
    ADD COLUMN ship_reviewed_by uuid REFERENCES "User"(user_id),
    ADD COLUMN dispatched_by uuid REFERENCES "User"(user_id),
    ADD COLUMN staffing_approved_revision bigint,
    DROP CONSTRAINT staffing_request_status_check,
    DROP CONSTRAINT staffing_request_ops_approval_check;

ALTER TABLE "StaffingRequest"
    ADD CONSTRAINT staffing_request_status_check CHECK (
        status IN ('PENDING_ASM','PENDING_SHIP_REVIEW','SENT_TO_HR','HR_SUBMITTED',
            'PLANNER_CONFIRMED','OPS_APPROVED','RETURNED','CANCELLED')
        OR (workflow_version='LEGACY' AND status IN ('SHIP_CONFIRMED','HR_APPROVED'))
        OR (workflow_version='V2' AND status='SHIP_REVIEWED')
    ),
    ADD CONSTRAINT staffing_request_ops_approval_check CHECK (
        status <> 'OPS_APPROVED' OR (
            hr_submitted_by IS NOT NULL AND ops_approved_by IS NOT NULL
            AND CASE WHEN workflow_version='V2' THEN
                ship_reviewed_by IS NOT NULL AND dispatched_by IS NOT NULL
                AND planner_confirmed_by IS NOT NULL AND staffing_approved_revision IS NOT NULL
                AND staffing_approved_revision=revision
            ELSE ship_confirmed_by IS NOT NULL AND (
                (ship_review_workflow AND hr_approved_by IS NOT NULL)
                OR (NOT ship_review_workflow AND planner_confirmed_by IS NOT NULL))
            END
        )
    );

CREATE TABLE "StaffingActiveArea" (
    request_id uuid NOT NULL REFERENCES "StaffingRequest"(request_id) ON DELETE CASCADE,
    area_id uuid NOT NULL REFERENCES "ShipModelObject"(ship_model_object_id),
    terminal_id uuid REFERENCES "PosTerminal"(terminal_id),
    PRIMARY KEY (request_id, area_id)
);

CREATE TABLE "StaffingNeed" (
    need_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL,
    area_id uuid NOT NULL,
    position_code varchar(32) NOT NULL CHECK (position_code IN ('ONBOARD_STAFF','SHIP_MANAGER','FINANCE_OFFICER')),
    required_count integer NOT NULL CHECK (required_count > 0),
    plan_revision bigint NOT NULL CHECK (plan_revision > 0),
    UNIQUE (request_id, area_id, position_code),
    UNIQUE (request_id, need_id),
    FOREIGN KEY (request_id, area_id) REFERENCES "StaffingActiveArea" ON DELETE CASCADE
);

CREATE TABLE "StaffingNeedAssignment" (
    request_id uuid NOT NULL,
    need_id uuid NOT NULL,
    cruise_staff_id uuid NOT NULL REFERENCES "CruiseStaff"(cruise_staff_id) ON DELETE CASCADE,
    PRIMARY KEY (request_id, cruise_staff_id),
    FOREIGN KEY (request_id, need_id) REFERENCES "StaffingNeed"(request_id, need_id) ON DELETE CASCADE
);

CREATE TABLE "StaffingServicePlacement" (
    request_id uuid NOT NULL,
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    area_id uuid NOT NULL,
    PRIMARY KEY (request_id, service_id),
    FOREIGN KEY (request_id, area_id) REFERENCES "StaffingActiveArea" ON DELETE CASCADE
);

CREATE VIEW "StaffingV2EligibleArea" AS
SELECT a.cruise_id, a.ship_id, a.area_id, a.area_name, o.deck_id
FROM "StaffingServingArea" a JOIN "ShipModelObject" o ON o.ship_model_object_id=a.area_id
JOIN "Deck" d ON d.deck_id=o.deck_id AND d.ship_id=a.ship_id
WHERE o.cabin_id IS NULL;

CREATE VIEW "StaffingV2ServicePlacement" AS
SELECT placement.request_id, placement.service_id, placement.area_id, area.terminal_id,
    jsonb_build_array(t.permissions, policy.permissions, assignment.permissions,
        assignment.inherit_permissions, permission.enabled, permission.permissions,
        permission.inherit_permissions) AS permissions_snapshot
FROM "StaffingServicePlacement" placement
JOIN "StaffingRequest" req USING (request_id)
JOIN "StaffingActiveArea" area USING (request_id, area_id)
JOIN "StaffingPlannedOnboardService" planned ON planned.cruise_id=req.cruise_id
    AND planned.service_id=placement.service_id
JOIN "Service" s ON s.service_id=placement.service_id AND s.status='ACTIVE'
JOIN "StaffingV2EligibleArea" eligible ON eligible.cruise_id=req.cruise_id AND eligible.area_id=area.area_id
JOIN "PosTerminal" t ON t.terminal_id=area.terminal_id AND t.ship_id=eligible.ship_id AND t.enabled
JOIN "DeckServiceAssignment" d ON d.deck_id=eligible.deck_id AND d.service_id=s.service_id
JOIN "AreaServiceAssignment" assignment ON assignment.area_object_id=area.area_id
    AND assignment.service_id=s.service_id AND assignment.enabled
JOIN "ServiceOperationPolicy" policy ON policy.service_id=s.service_id
LEFT JOIN "PosAreaServicePermission" permission ON permission.terminal_id=t.terminal_id
    AND permission.area_object_id=area.area_id AND permission.service_id=s.service_id
WHERE 'SERVICE_READ'=ANY(policy.permissions) AND 'SERVICE_READ'=ANY(t.permissions)
    AND (assignment.inherit_permissions OR 'SERVICE_READ'=ANY(assignment.permissions))
    AND (permission.service_id IS NULL OR permission.enabled
        AND (permission.inherit_permissions OR 'SERVICE_READ'=ANY(permission.permissions)));

CREATE VIEW "StaffingV2QualifiedAssignment" AS
SELECT assignment.request_id, assignment.need_id, cs.user_id
FROM "StaffingNeedAssignment" assignment JOIN "StaffingNeed" need USING (request_id, need_id)
JOIN "StaffingRequest" req USING (request_id)
JOIN "CruiseStaff" cs ON cs.cruise_staff_id=assignment.cruise_staff_id AND cs.cruise_id=req.cruise_id
    AND cs.status='ACTIVE' AND cs.role_in_cruise=need.position_code
JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
WHERE EXISTS (SELECT 1 FROM "UserRole" ur
    WHERE ur.user_id=cs.user_id AND ur.role=need.position_code)
AND NOT EXISTS (SELECT 1 FROM "UserRole" ur
    WHERE ur.user_id=cs.user_id AND ur.role='ADMIN');

CREATE OR REPLACE FUNCTION reset_staffing_approval(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "StaffingRequest"
    SET status='SENT_TO_HR', revision=revision+1, updated_at=now(),
        hr_submitted_by=NULL, ship_confirmed_by=NULL, planner_confirmed_by=NULL,
        ops_approved_by=NULL, hr_approved_by=NULL, staffing_approved_revision=NULL
    WHERE cruise_id=affected_cruise
        AND status IN ('HR_SUBMITTED','SHIP_CONFIRMED','HR_APPROVED','PLANNER_CONFIRMED','OPS_APPROVED');
    SELECT invalidate_staffing_preparation(affected_cruise);
$$;

-- Source changes still use invalidate_staffing_plan. During preparation, V2 follows
-- the ASM handoff and validates its live program fingerprint after ship review.
CREATE FUNCTION invalidate_staffing_legacy_plan(affected_cruise uuid) RETURNS void LANGUAGE sql AS $$
    UPDATE "StaffingRequest"
    SET status='CANCELLED', revision=revision+1, updated_at=now()
    WHERE cruise_id=affected_cruise AND workflow_version='LEGACY' AND status NOT IN ('CANCELLED','RETURNED');
    SELECT invalidate_staffing_preparation(affected_cruise);
$$;
