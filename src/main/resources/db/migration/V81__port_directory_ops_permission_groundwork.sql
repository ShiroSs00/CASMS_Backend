-- PORT-01: expand the existing permission allowlist without removing any prior value/grant.
-- No PortCallWindow business tables, accounts, status migration or directory-data writes.
DO $$
DECLARE old_expression text;
BEGIN
    SELECT pg_get_expr(conbin, conrelid) INTO old_expression
    FROM pg_constraint
    WHERE conrelid='"RolePermissionMapping"'::regclass AND conname='RolePermissionMapping_permission_check';
    IF old_expression IS NOT NULL THEN
        ALTER TABLE "RolePermissionMapping" DROP CONSTRAINT "RolePermissionMapping_permission_check";
        EXECUTE format('ALTER TABLE "RolePermissionMapping" ADD CONSTRAINT "RolePermissionMapping_permission_check" CHECK ((%s) OR permission IN (''PORT_CALL_WINDOW_READ'',''PORT_CALL_WINDOW_CREATE'',''PORT_CALL_WINDOW_UPDATE'',''PORT_CALL_WINDOW_VERIFY''))', old_expression);
    END IF;
END $$;

INSERT INTO "RolePermission" (role) VALUES ('OPERATIONS_MANAGER') ON CONFLICT DO NOTHING;
INSERT INTO "RolePermissionMapping" (role,permission)
SELECT 'OPERATIONS_MANAGER', p FROM unnest(ARRAY[
    'PORT_READ','PORT_CREATE','PORT_UPDATE',
    'DESTINATION_READ','DESTINATION_CREATE','DESTINATION_UPDATE',
    'PORT_CALL_WINDOW_READ','PORT_CALL_WINDOW_CREATE','PORT_CALL_WINDOW_UPDATE','PORT_CALL_WINDOW_VERIFY'
]::text[]) AS requested(p)
WHERE NOT EXISTS (SELECT 1 FROM "RolePermissionMapping" existing
                  WHERE existing.role='OPERATIONS_MANAGER' AND existing.permission=requested.p);
