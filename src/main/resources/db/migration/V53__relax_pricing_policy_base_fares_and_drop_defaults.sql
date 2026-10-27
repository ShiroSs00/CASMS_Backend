-- V53: Relax CruisePricingPolicy base fare constraints and drop demo defaults (BE-H01)
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN interior_base_fare DROP DEFAULT;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN interior_base_fare DROP NOT NULL;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN ocean_view_base_fare DROP DEFAULT;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN ocean_view_base_fare DROP NOT NULL;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN balcony_base_fare DROP DEFAULT;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN balcony_base_fare DROP NOT NULL;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN suite_base_fare DROP DEFAULT;
ALTER TABLE "CruisePricingPolicy" ALTER COLUMN suite_base_fare DROP NOT NULL;
