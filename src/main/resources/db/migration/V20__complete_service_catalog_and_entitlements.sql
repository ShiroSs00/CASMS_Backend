-- Additive service/package model. Existing rows remain valid; new fields are nullable or have safe defaults.
CREATE TABLE "ServiceCategory" (
  "service_category_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "category_code" varchar(50) NOT NULL UNIQUE,
  "category_name" varchar(150) NOT NULL,
  "parent_category_id" uuid REFERENCES "ServiceCategory"("service_category_id"),
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now(),
  CHECK ("parent_category_id" IS NULL OR "parent_category_id" <> "service_category_id")
);

ALTER TABLE "Service"
  ADD COLUMN "service_category_id" uuid REFERENCES "ServiceCategory"("service_category_id"),
  ADD COLUMN "unit_code" varchar(30),
  ADD COLUMN "can_sell_independently" boolean NOT NULL DEFAULT true,
  ADD COLUMN "can_buy_pre_cruise" boolean NOT NULL DEFAULT true,
  ADD COLUMN "can_buy_onboard" boolean NOT NULL DEFAULT true,
  ADD COLUMN "requires_schedule" boolean NOT NULL DEFAULT false,
  ADD COLUMN "has_allowance" boolean NOT NULL DEFAULT false,
  ADD COLUMN "applicability_scope" varchar(20) NOT NULL DEFAULT 'PASSENGER',
  ADD COLUMN "requires_staff" boolean NOT NULL DEFAULT false,
  ADD COLUMN "duration_minutes" integer,
  ADD COLUMN "advance_notice_minutes" integer,
  ADD COLUMN "minimum_age" integer,
  ADD COLUMN "maximum_age" integer,
  ADD COLUMN "usage_terms" text,
  ADD COLUMN "cancellation_terms" text,
  ADD CONSTRAINT "service_applicability_scope_check"
    CHECK ("applicability_scope" IN ('PASSENGER','CABIN','BOOKING','GROUP')),
  ADD CONSTRAINT "service_duration_check" CHECK ("duration_minutes" IS NULL OR "duration_minutes" > 0),
  ADD CONSTRAINT "service_advance_notice_check" CHECK ("advance_notice_minutes" IS NULL OR "advance_notice_minutes" >= 0),
  ADD CONSTRAINT "service_age_range_check" CHECK ("minimum_age" IS NULL OR "maximum_age" IS NULL OR "maximum_age" >= "minimum_age");

ALTER TYPE "TourPackage_package_type" ADD VALUE IF NOT EXISTS 'PLUS';
ALTER TYPE "TourPackage_package_type" ADD VALUE IF NOT EXISTS 'PREMIUM_PLUS';
ALTER TABLE "TourPackage"
  ADD COLUMN "cabin_selection_policy" varchar(30) NOT NULL DEFAULT 'AUTO_ASSIGN',
  ADD COLUMN "parent_package_id" uuid REFERENCES "TourPackage"("tour_package_id"),
  ADD CONSTRAINT "tour_package_cabin_selection_check"
    CHECK ("cabin_selection_policy" IN ('AUTO_ASSIGN','LOCATION_PREFERENCE','SPECIFIC_CABIN','PRIORITY_LOCATION')),
  ADD CONSTRAINT "tour_package_not_own_parent_check"
    CHECK ("parent_package_id" IS NULL OR "parent_package_id" <> "tour_package_id");

CREATE TABLE "PackageEntitlement" (
  "package_entitlement_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "tour_package_id" uuid NOT NULL REFERENCES "TourPackage"("tour_package_id") ON DELETE CASCADE,
  "service_category_id" uuid NOT NULL REFERENCES "ServiceCategory"("service_category_id"),
  "benefit_type" varchar(20) NOT NULL,
  "quantity_limit" integer,
  "unit_code" varchar(30),
  "discount_percent" numeric(5,2),
  "owner_scope" varchar(20) NOT NULL DEFAULT 'PASSENGER',
  "created_at" timestamptz NOT NULL DEFAULT now(),
  CHECK ("benefit_type" IN ('INCLUDED','LIMITED','DISCOUNT','NOT_INCLUDED')),
  CHECK ("quantity_limit" IS NULL OR "quantity_limit" >= 0),
  CHECK ("discount_percent" IS NULL OR ("discount_percent" >= 0 AND "discount_percent" <= 100)),
  CHECK ("owner_scope" IN ('PASSENGER','CABIN','BOOKING','GROUP'))
);
CREATE UNIQUE INDEX "package_entitlement_category_unique"
  ON "PackageEntitlement" ("tour_package_id", "service_category_id");

-- Existing package-to-service membership is retained as the canonical service-specific entitlement link.
ALTER TABLE "TourPackageService"
  ADD COLUMN "quantity_limit" integer,
  ADD COLUMN "unit_code" varchar(30),
  ADD COLUMN "discount_percent" numeric(5,2),
  ADD COLUMN "owner_scope" varchar(20) NOT NULL DEFAULT 'PASSENGER',
  ADD CONSTRAINT "package_service_quantity_check" CHECK ("quantity_limit" IS NULL OR "quantity_limit" >= 0),
  ADD CONSTRAINT "package_service_discount_check" CHECK ("discount_percent" IS NULL OR ("discount_percent" >= 0 AND "discount_percent" <= 100)),
  ADD CONSTRAINT "package_service_owner_scope_check" CHECK ("owner_scope" IN ('PASSENGER','CABIN','BOOKING','GROUP'));

CREATE TABLE "ServiceSchedule" (
  "service_schedule_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "schedule_code" varchar(60) NOT NULL UNIQUE,
  "service_id" uuid NOT NULL REFERENCES "Service"("service_id"),
  "cruise_id" uuid REFERENCES "Cruise"("cruise_id"),
  "cruise_stop_id" uuid REFERENCES "CruiseStop"("cruise_stop_id"),
  "supplier_id" uuid REFERENCES "Supplier"("supplier_id"),
  "starts_at" timestamptz NOT NULL,
  "ends_at" timestamptz NOT NULL,
  "location" varchar(250) NOT NULL,
  "capacity" integer NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'OPEN',
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now(),
  CHECK ("ends_at" > "starts_at"),
  CHECK ("capacity" > 0),
  CHECK ("status" IN ('DRAFT','OPEN','FULL','CLOSED','CANCELLED'))
);
CREATE INDEX "service_schedule_service_time_idx" ON "ServiceSchedule" ("service_id", "starts_at", "status");
CREATE INDEX "service_schedule_cruise_time_idx" ON "ServiceSchedule" ("cruise_id", "starts_at");

ALTER TABLE "ServiceRequest"
  ADD COLUMN "service_schedule_id" uuid REFERENCES "ServiceSchedule"("service_schedule_id"),
  ADD COLUMN "unit_price_snapshot" numeric(19,4),
  ADD COLUMN "currency_snapshot" char(3),
  ADD COLUMN "idempotency_key" varchar(100),
  ADD CONSTRAINT "service_request_price_snapshot_check"
    CHECK ("unit_price_snapshot" IS NULL OR "unit_price_snapshot" >= 0);
CREATE UNIQUE INDEX "service_request_idempotency_unique"
  ON "ServiceRequest" ("idempotency_key") WHERE "idempotency_key" IS NOT NULL;
CREATE INDEX "service_request_schedule_idx" ON "ServiceRequest" ("service_schedule_id", "status");

CREATE TABLE "ServiceUsage" (
  "service_usage_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "service_request_id" uuid NOT NULL REFERENCES "ServiceRequest"("service_request_id"),
  "quantity" integer NOT NULL,
  "used_at" timestamptz NOT NULL DEFAULT now(),
  "recorded_by_user_id" uuid REFERENCES "User"("user_id"),
  "note" varchar(500),
  CHECK ("quantity" > 0)
);
CREATE INDEX "service_usage_request_idx" ON "ServiceUsage" ("service_request_id", "used_at");

CREATE TABLE "ServiceAdditionalCharge" (
  "service_additional_charge_id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "service_request_id" uuid NOT NULL REFERENCES "ServiceRequest"("service_request_id"),
  "booking_passenger_id" uuid REFERENCES "BookingPassenger"("booking_passenger_id"),
  "charge_id" uuid REFERENCES "Charge"("charge_id"),
  "charge_code" varchar(50) NOT NULL UNIQUE,
  "reason" varchar(30) NOT NULL,
  "quantity" integer NOT NULL,
  "unit_price" numeric(19,4) NOT NULL,
  "currency_code" char(3) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "idempotency_key" varchar(100) UNIQUE,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  CHECK ("reason" IN ('PURCHASE','OVERAGE','UPGRADE')),
  CHECK ("quantity" > 0 AND "unit_price" >= 0),
  CHECK ("status" IN ('PENDING','POSTED','VOIDED','REFUNDED'))
);
CREATE INDEX "service_additional_charge_request_idx" ON "ServiceAdditionalCharge" ("service_request_id", "status");

-- Cabin is the existing inventory entity; extend it without creating a duplicate cabin model.
ALTER TABLE "Cabin"
  ADD COLUMN "minimum_occupancy" integer NOT NULL DEFAULT 1,
  ADD COLUMN "bed_count" integer,
  ADD COLUMN "bed_configuration" varchar(100),
  ADD COLUMN "area_square_meters" numeric(8,2),
  ADD COLUMN "balcony_area_square_meters" numeric(8,2),
  ADD COLUMN "location_code" varchar(30),
  ADD COLUMN "has_balcony" boolean NOT NULL DEFAULT false,
  ADD COLUMN "has_connecting_door" boolean NOT NULL DEFAULT false,
  ADD COLUMN "is_family_cabin" boolean NOT NULL DEFAULT false,
  ADD COLUMN "has_bathtub" boolean NOT NULL DEFAULT false,
  ADD COLUMN "has_separate_living_area" boolean NOT NULL DEFAULT false,
  ADD CONSTRAINT "cabin_occupancy_range_check" CHECK ("minimum_occupancy" > 0 AND "minimum_occupancy" <= "capacity"),
  ADD CONSTRAINT "cabin_bed_count_check" CHECK ("bed_count" IS NULL OR "bed_count" > 0),
  ADD CONSTRAINT "cabin_area_check" CHECK (("area_square_meters" IS NULL OR "area_square_meters" > 0) AND ("balcony_area_square_meters" IS NULL OR "balcony_area_square_meters" >= 0)),
  ADD CONSTRAINT "cabin_balcony_type_check" CHECK (NOT "has_balcony" OR "cabin_type" = 'BALCONY' OR "cabin_type" = 'SUITE');

CREATE INDEX "service_catalog_category_status_idx" ON "Service" ("service_category_id", "status");
CREATE INDEX "package_entitlement_package_idx" ON "PackageEntitlement" ("tour_package_id");
