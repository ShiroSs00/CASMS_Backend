CREATE TABLE "CruiseOperatingBudgetReview" (
    cruise_id uuid PRIMARY KEY REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    revision bigint NOT NULL CHECK (revision > 0),
    status varchar(20) NOT NULL CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
    submitted_by uuid NOT NULL REFERENCES "User"(user_id),
    reason text
);

ALTER TABLE "CruisePreparationEvent"
    ALTER COLUMN actor_id DROP NOT NULL,
    ADD COLUMN previous_status varchar(30),
    ADD COLUMN previous_revision bigint;

INSERT INTO "RolePermissionMapping" (role, permission)
VALUES ('SHIP_MANAGER','CRUISE_PREPARATION_SUBMIT'),
       ('OPERATIONS_MANAGER','CRUISE_PREPARATION_SUBMIT')
ON CONFLICT DO NOTHING;
