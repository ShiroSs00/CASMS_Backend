-- v4.1 U-PRICE: keep approved rows and amounts intact; successor policies get new IDs.
ALTER TABLE "CruisePricingPolicy" DROP CONSTRAINT uq_cruise_pricing_policy;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN policy_number integer NOT NULL DEFAULT 1;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN revision bigint NOT NULL DEFAULT 0;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN submitted_by uuid REFERENCES "User"(user_id);
ALTER TABLE "CruisePricingPolicy" ADD COLUMN submitted_at timestamptz;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN reviewed_by uuid REFERENCES "User"(user_id);
ALTER TABLE "CruisePricingPolicy" ADD COLUMN reviewed_at timestamptz;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN decision_reason text;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN submitted_snapshot text;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN snapshot_hash varchar(64);
ALTER TABLE "CruisePricingPolicy" ADD CONSTRAINT uq_cruise_pricing_number UNIQUE(cruise_id,policy_number);
CREATE TABLE "CruisePricingMaker"(
 pricing_policy_id uuid REFERENCES "CruisePricingPolicy"(pricing_policy_id),
 user_id uuid REFERENCES "User"(user_id), PRIMARY KEY(pricing_policy_id,user_id)
);
CREATE TABLE "CruisePricingEvent"(
 event_id uuid PRIMARY KEY, pricing_policy_id uuid NOT NULL REFERENCES "CruisePricingPolicy"(pricing_policy_id),
 cruise_id uuid NOT NULL REFERENCES "Cruise"(cruise_id), revision bigint NOT NULL,
 action varchar(24) NOT NULL, actor_id uuid NOT NULL REFERENCES "User"(user_id),
 reason text, snapshot text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
-- Preserve the existing permission allowlist and add only this decided permission.
DO $$ DECLARE expression text; BEGIN
 SELECT pg_get_expr(conbin,conrelid) INTO expression FROM pg_constraint
 WHERE conrelid='"RolePermissionMapping"'::regclass AND conname='RolePermissionMapping_permission_check';
 IF expression IS NULL THEN RAISE EXCEPTION 'Expected permission allowlist missing'; END IF;
 ALTER TABLE "RolePermissionMapping" DROP CONSTRAINT "RolePermissionMapping_permission_check";
 EXECUTE format('ALTER TABLE "RolePermissionMapping" ADD CONSTRAINT "RolePermissionMapping_permission_check" CHECK ((%s) OR permission::text = %L)',expression,'CRUISE_PRICING_APPROVE');
END $$;
INSERT INTO "RolePermissionMapping"(role,permission)
 SELECT 'OPERATIONS_MANAGER','CRUISE_PRICING_APPROVE'
 WHERE NOT EXISTS(SELECT 1 FROM "RolePermissionMapping" WHERE role='OPERATIONS_MANAGER' AND permission='CRUISE_PRICING_APPROVE');
