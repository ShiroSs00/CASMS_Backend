-- V52: Enhance CruisePricingPolicy with status, approval tracking, and cancellation policy (BE-H01)
ALTER TABLE "CruisePricingPolicy" ADD COLUMN IF NOT EXISTS "status" VARCHAR(30) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE "CruisePricingPolicy" ADD COLUMN IF NOT EXISTS "cancellation_policy" TEXT;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN IF NOT EXISTS "approved_by" UUID REFERENCES "User"(user_id) ON DELETE SET NULL;
ALTER TABLE "CruisePricingPolicy" ADD COLUMN IF NOT EXISTS "approved_at" TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_cruise_pricing_policy_status ON "CruisePricingPolicy"(status);
