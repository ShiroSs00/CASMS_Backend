-- Preserve assignment history and record who created/ended each assignment.
ALTER TABLE "CruiseStaff"
    ADD COLUMN IF NOT EXISTS assigned_by_user_id uuid,
    ADD COLUMN IF NOT EXISTS ended_at timestamptz,
    ADD COLUMN IF NOT EXISTS ended_by_user_id uuid;

ALTER TABLE "CruiseStaff"
    ADD CONSTRAINT fk_cruise_staff_assigned_by
        FOREIGN KEY (assigned_by_user_id) REFERENCES "User"(user_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_cruise_staff_ended_by
        FOREIGN KEY (ended_by_user_id) REFERENCES "User"(user_id) ON DELETE SET NULL;

ALTER TABLE "CruiseStaff" DROP CONSTRAINT IF EXISTS uq_cruise_staff_user;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cruise_staff_active_user
    ON "CruiseStaff" (cruise_id, user_id) WHERE status = 'ACTIVE';

-- Reduce any previously persisted ADMIN grants to the current system-only role.
DELETE FROM "RolePermissionMapping"
WHERE role = 'ADMIN'
  AND permission NOT IN (
      'USER_CREATE', 'USER_READ', 'USER_UPDATE', 'USER_ROLE_ASSIGN', 'USER_STATUS_CHANGE',
      'SHIP_MODEL_CREATE', 'SHIP_MODEL_READ', 'SHIP_MODEL_UPDATE', 'SHIP_MODEL_DELETE',
      'SHIP_MODEL_PUBLISH', 'SHIP_MODEL_OBJECT_CREATE', 'SHIP_MODEL_OBJECT_UPDATE',
      'SHIP_MODEL_OBJECT_DELETE', 'SHIP_CONFIGURATION_READ', 'SHIP_CONFIGURATION_VALIDATE',
      'BLE_ANCHOR_CREATE', 'BLE_ANCHOR_READ', 'BLE_ANCHOR_UPDATE', 'BLE_ANCHOR_DELETE',
      'BLE_ANCHOR_ACTIVATE', 'BLE_ANCHOR_DEACTIVATE', 'BLE_ANCHOR_RETIRE',
      'BLE_ANCHOR_INSTALL', 'BLE_ANCHOR_VALIDATE', 'BLE_TELEMETRY_RECEIVE', 'BLE_TELEMETRY_READ'
  );
