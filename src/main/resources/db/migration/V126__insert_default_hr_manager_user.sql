-- Seed default HR_MANAGER account (hr_manager / hr123)
INSERT INTO "RolePermission" (role)
VALUES ('HR_MANAGER')
ON CONFLICT (role) DO NOTHING;

INSERT INTO "User" (user_id, username, password_hash, email, status, user_type, created_at, updated_at)
SELECT
    'a0000000-0000-0000-0000-000000000017'::uuid,
    'hr_manager',
    '$2a$10$vqlVZ6tpMa/n6JjZTAy3feMk/3V0XMfmXCMc0uGJXTSPWyoGiXp92',
    'hr.manager@casms.com',
    'ACTIVE',
    'STAFF',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM "User"
    WHERE user_id = 'a0000000-0000-0000-0000-000000000017'::uuid
       OR lower(username) = 'hr_manager'
       OR lower(email) = 'hr.manager@casms.com'
);

INSERT INTO "UserRole" (user_id, role)
SELECT u.user_id, 'HR_MANAGER'
FROM "User" u
WHERE lower(u.username) = 'hr_manager'
  AND NOT EXISTS (
      SELECT 1 FROM "UserRole" ur
      WHERE ur.user_id = u.user_id AND ur.role = 'HR_MANAGER'
  );
