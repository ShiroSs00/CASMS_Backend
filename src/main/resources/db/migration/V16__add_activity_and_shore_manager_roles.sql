-- Migration V16: Add ACTIVITY_MANAGER and SHORE_MANAGER to role check constraints
ALTER TABLE "UserRole" DROP CONSTRAINT IF EXISTS "UserRole_role_check";
ALTER TABLE "UserRole" ADD CONSTRAINT "UserRole_role_check" CHECK (
    (role)::text = ANY (ARRAY[
        'ADMIN', 'CRUISE_MANAGER', 'SHIP_MANAGER', 'OPERATIONS_MANAGER',
        'BOOKING_AGENT', 'CHECKIN_STAFF', 'ONBOARD_STAFF', 'FINANCE_OFFICER',
        'CUSTOMER_SERVICE', 'PASSENGER', 'ACTIVITY_MANAGER', 'SHORE_MANAGER'
    ]::text[])
);

ALTER TABLE "RolePermission" DROP CONSTRAINT IF EXISTS "RolePermission_role_check";
ALTER TABLE "RolePermission" ADD CONSTRAINT "RolePermission_role_check" CHECK (
    (role)::text = ANY (ARRAY[
        'ADMIN', 'CRUISE_MANAGER', 'SHIP_MANAGER', 'OPERATIONS_MANAGER',
        'BOOKING_AGENT', 'CHECKIN_STAFF', 'ONBOARD_STAFF', 'FINANCE_OFFICER',
        'CUSTOMER_SERVICE', 'PASSENGER', 'ACTIVITY_MANAGER', 'SHORE_MANAGER'
    ]::text[])
);

-- Insert roles into RolePermission
INSERT INTO "RolePermission" (role) VALUES
    ('ACTIVITY_MANAGER'),
    ('SHORE_MANAGER')
ON CONFLICT (role) DO NOTHING;

-- Insert default user accounts for ACTIVITY_MANAGER and SHORE_MANAGER (admin123 password)
INSERT INTO "User" (user_id, username, password_hash, email, status, user_type, created_at, updated_at) VALUES
    ('a0000000-0000-0000-0000-000000000015', 'activity_manager', '$2a$10$kFRbMu8VP8UH76WNaVtrXefN8TldcFz8bi2TvDZVDd9a0J9xA85ku', 'activity.manager@casms.com', 'ACTIVE', 'STAFF', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000016', 'shore_manager', '$2a$10$kFRbMu8VP8UH76WNaVtrXefN8TldcFz8bi2TvDZVDd9a0J9xA85ku', 'shore.manager@casms.com', 'ACTIVE', 'STAFF', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

-- Assign roles to default users
INSERT INTO "UserRole" (user_id, role)
SELECT incoming.user_id::uuid, incoming.role
FROM (VALUES
    ('a0000000-0000-0000-0000-000000000015', 'ACTIVITY_MANAGER'),
    ('a0000000-0000-0000-0000-000000000016', 'SHORE_MANAGER')
) AS incoming(user_id, role)
WHERE NOT EXISTS (
    SELECT 1 FROM "UserRole" existing
    WHERE existing.user_id = incoming.user_id::uuid
      AND existing.role = incoming.role
);
