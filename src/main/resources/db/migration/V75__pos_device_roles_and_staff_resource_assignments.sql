-- Retain accounts and unrelated role assignments; only retire the requested roles.
ALTER TABLE "UserRole" DROP CONSTRAINT IF EXISTS "UserRole_role_check";
ALTER TABLE "RolePermission" DROP CONSTRAINT IF EXISTS "RolePermission_role_check";
INSERT INTO "RolePermission" (role) VALUES ('POS') ON CONFLICT DO NOTHING;
INSERT INTO "UserRole" (user_id,role)
SELECT user_id,'POS' FROM "UserRole" WHERE role='CUSTOMER_SERVICE' ON CONFLICT DO NOTHING;
DELETE FROM "UserRole" WHERE role IN ('CUSTOMER_SERVICE','CHECKIN_STAFF');
DELETE FROM "RolePermissionMapping" WHERE role IN ('CUSTOMER_SERVICE','CHECKIN_STAFF','POS');
DELETE FROM "RolePermission" WHERE role IN ('CUSTOMER_SERVICE','CHECKIN_STAFF');
ALTER TABLE "UserRole" ADD CONSTRAINT "UserRole_role_check" CHECK (role::text = ANY (ARRAY[
    'ADMIN','CRUISE_MANAGER','SHIP_MANAGER','OPERATIONS_MANAGER','BOOKING_AGENT',
    'ONBOARD_STAFF','FINANCE_OFFICER','POS','PASSENGER','ACTIVITY_SERVICE_MANAGER']::text[]));
ALTER TABLE "RolePermission" ADD CONSTRAINT "RolePermission_role_check" CHECK (role::text = ANY (ARRAY[
    'ADMIN','CRUISE_MANAGER','SHIP_MANAGER','OPERATIONS_MANAGER','BOOKING_AGENT',
    'ONBOARD_STAFF','FINANCE_OFFICER','POS','PASSENGER','ACTIVITY_SERVICE_MANAGER']::text[]));

ALTER TABLE "PosTerminal" ADD COLUMN device_user_id uuid REFERENCES "User"(user_id);
CREATE UNIQUE INDEX uk_pos_terminal_device_user ON "PosTerminal"(device_user_id) WHERE device_user_id IS NOT NULL;
ALTER TABLE "PosSession" ADD COLUMN area_object_id uuid REFERENCES "ShipModelObject"(ship_model_object_id);
ALTER TABLE "PosSession" ADD COLUMN cruise_id uuid REFERENCES "Cruise"(cruise_id);
UPDATE "PosSession" p SET area_object_id=a.area_object_id,cruise_id=cs.cruise_id
FROM "AreaStaffAssignment" a JOIN "CruiseStaff" cs ON cs.cruise_staff_id=a.cruise_staff_id
WHERE p.area_staff_assignment_id=a.assignment_id;
-- Old sessions predate resource-level employee consent; require a fresh login.
UPDATE "PosSession" SET revoked_at=COALESCE(revoked_at,now());

CREATE TABLE "StaffServiceAssignment" (
    assignment_id uuid NOT NULL REFERENCES "AreaStaffAssignment"(assignment_id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES "Service"(service_id),
    permissions text[] NOT NULL DEFAULT '{}',
    inherit_permissions boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(assignment_id,service_id),
    CHECK(permissions <@ ARRAY['SERVICE_READ','SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN','SERVICE_REQUEST_EXECUTE','SERVICE_SALE']::text[])
);
CREATE TABLE "StaffActivityAssignment" (
    assignment_id uuid NOT NULL REFERENCES "AreaStaffAssignment"(assignment_id) ON DELETE CASCADE,
    activity_id uuid NOT NULL REFERENCES "OnboardActivity"(onboard_activity_id),
    permissions text[] NOT NULL DEFAULT '{}',
    inherit_permissions boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(assignment_id,activity_id),
    CHECK(permissions <@ ARRAY['ACTIVITY_READ','ACTIVITY_REGISTRATION','ACTIVITY_ATTENDANCE','ACTIVITY_LIFECYCLE']::text[])
);
-- No automatic grants: managers explicitly assign the employee's service/activity.
DELETE FROM "RolePermissionMapping" WHERE role='ONBOARD_STAFF' AND permission IN (
    'SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN','SERVICE_REQUEST_EXECUTE',
    'PRODUCT_SALE','PAYMENT_PROCESS','ACTIVITY_ATTENDANCE','PARTICIPATION_ATTENDANCE','TOUR_GROUP_PASSENGER_ATTENDANCE');
INSERT INTO "RolePermissionMapping" (role,permission) VALUES ('PASSENGER','CHECKIN_UPDATE') ON CONFLICT DO NOTHING;
INSERT INTO "RolePermissionMapping" (role,permission)
SELECT 'OPERATIONS_MANAGER',p FROM unnest(ARRAY['PASSENGER_BLE_ASSIGN','PASSENGER_BLE_READ','PASSENGER_BLE_REVOKE','PASSENGER_BLE_HISTORY']) p ON CONFLICT DO NOTHING;

-- ADMIN owns physical/model configuration. SHIP_MANAGER retains operational status.
DELETE FROM "RolePermissionMapping" WHERE role='SHIP_MANAGER' AND permission IN (
    'SHIP_CREATE','SHIP_UPDATE','SHIP_DELETE','SHIP_MODEL_CREATE','SHIP_MODEL_UPDATE','SHIP_MODEL_DELETE','SHIP_MODEL_PUBLISH',
    'SHIP_MODEL_OBJECT_CREATE','SHIP_MODEL_OBJECT_UPDATE','SHIP_MODEL_OBJECT_DELETE',
    'DECK_CREATE','DECK_UPDATE','DECK_DELETE','CABIN_CREATE','CABIN_UPDATE','CABIN_DELETE',
    'BLE_ANCHOR_CREATE','BLE_ANCHOR_UPDATE','BLE_ANCHOR_DELETE','BLE_ANCHOR_INSTALL');
INSERT INTO "RolePermissionMapping" (role,permission)
SELECT 'ADMIN',p FROM unnest(ARRAY['SHIP_DELETE','SHIP_VALIDATE','DECK_CREATE','DECK_READ','DECK_UPDATE','DECK_DELETE','DECK_CABINS_VIEW','DECK_VALIDATE',
    'CABIN_CREATE','CABIN_READ','CABIN_UPDATE','CABIN_DELETE','CABIN_SEARCH','CABIN_VALIDATE']) p ON CONFLICT DO NOTHING;
