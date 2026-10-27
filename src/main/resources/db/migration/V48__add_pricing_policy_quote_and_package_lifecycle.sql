-- V48: Add CruisePricingPolicy, BookingQuote, and Package/Excursion lifecycle attributes

-- 1. Table CruisePricingPolicy
CREATE TABLE IF NOT EXISTS "CruisePricingPolicy" (
    pricing_policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cruise_id UUID REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    policy_version VARCHAR(50) NOT NULL DEFAULT 'PRC-2026.10-V1',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    interior_base_fare DECIMAL(19,4) NOT NULL DEFAULT 300.0000,
    ocean_view_base_fare DECIMAL(19,4) NOT NULL DEFAULT 450.0000,
    balcony_base_fare DECIMAL(19,4) NOT NULL DEFAULT 650.0000,
    suite_base_fare DECIMAL(19,4) NOT NULL DEFAULT 1100.0000,
    port_fee_per_passenger DECIMAL(19,4) NOT NULL DEFAULT 50.0000,
    tax_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0800,
    deposit_percentage DECIMAL(5,4) NOT NULL DEFAULT 0.3000,
    final_payment_due_days INT NOT NULL DEFAULT 14,
    full_payment_cutoff_days INT NOT NULL DEFAULT 7,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cruise_pricing_policy UNIQUE (cruise_id)
);

CREATE INDEX IF NOT EXISTS idx_cruise_pricing_policy_cruise_id ON "CruisePricingPolicy"(cruise_id);

-- 2. Table BookingQuote
CREATE TABLE IF NOT EXISTS "BookingQuote" (
    quote_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_code VARCHAR(50) NOT NULL UNIQUE,
    cruise_id UUID NOT NULL REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    user_id UUID REFERENCES "User"(user_id) ON DELETE SET NULL,
    policy_version VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal_amount DECIMAL(19,4) NOT NULL,
    taxes_and_fees_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(19,4) NOT NULL,
    deposit_amount DECIMAL(19,4) NOT NULL,
    payment_plan VARCHAR(30) NOT NULL DEFAULT 'DEPOSIT',
    final_payment_due_at TIMESTAMPTZ NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    quote_payload_json TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_booking_quote_cruise_expires ON "BookingQuote"(cruise_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_booking_quote_code ON "BookingQuote"(quote_code);

-- 3. Add quote reference & pricing policy version to Booking
ALTER TABLE "Booking" ADD COLUMN IF NOT EXISTS "quote_id" UUID REFERENCES "BookingQuote"(quote_id) ON DELETE SET NULL;
ALTER TABLE "Booking" ADD COLUMN IF NOT EXISTS "pricing_policy_version" VARCHAR(50);

-- 4. Add mandatory and entitlement fields to CruiseTourPackage and ShoreExcursion
ALTER TABLE "CruiseTourPackage" ADD COLUMN IF NOT EXISTS "is_mandatory" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE "CruiseTourPackage" ADD COLUMN IF NOT EXISTS "entitlement_type" VARCHAR(30) NOT NULL DEFAULT 'OPTIONAL_ADDON';

ALTER TABLE "ShoreExcursion" ADD COLUMN IF NOT EXISTS "is_mandatory" BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. Add sale audit fields to Cruise
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS "sale_scope" VARCHAR(30);
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS "opened_for_sale_at" TIMESTAMPTZ;
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS "opened_for_sale_by" UUID REFERENCES "User"(user_id) ON DELETE SET NULL;
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS "sale_event_identity" VARCHAR(100);
