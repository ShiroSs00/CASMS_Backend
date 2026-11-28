-- RG-12: post-HR Ship confirmation is distinct from the pre-HR ship review.
-- Preserve historical approvals and audit rows. Existing V2 approvals without
-- confirmation are no longer operationally ready (service rechecks evidence).
ALTER TABLE "StaffingRequest" DROP CONSTRAINT staffing_request_status_check;
ALTER TABLE "StaffingRequest" ADD CONSTRAINT staffing_request_status_check CHECK (
    status IN ('PENDING_ASM','PENDING_SHIP_REVIEW','SENT_TO_HR','HR_SUBMITTED',
        'SHIP_CONFIRMED','PLANNER_CONFIRMED','OPS_APPROVED','RETURNED','CANCELLED')
    OR (workflow_version='LEGACY' AND status='HR_APPROVED')
    OR (workflow_version='V2' AND status='SHIP_REVIEWED')
);
-- NOT VALID retains existing evidence without fabricating confirmations.
-- PostgreSQL enforces this for subsequent inserted/updated rows.
ALTER TABLE "StaffingRequest" ADD CONSTRAINT staffing_v2_ship_confirmation_check CHECK (
    workflow_version <> 'V2' OR status NOT IN ('SHIP_CONFIRMED','PLANNER_CONFIRMED','OPS_APPROVED')
    OR (ship_confirmed_by IS NOT NULL AND hr_submitted_by IS NOT NULL
        AND ship_confirmed_by <> hr_submitted_by AND ship_confirmed_by <> requested_by)
) NOT VALID;
