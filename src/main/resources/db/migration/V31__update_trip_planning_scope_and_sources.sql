-- V31__update_trip_planning_scope_and_sources.sql
-- Add duration_group, domestic_regions, country_codes to Trip
ALTER TABLE "Trip" ADD COLUMN IF NOT EXISTS duration_group VARCHAR(20) DEFAULT 'SHORT';
ALTER TABLE "Trip" ADD COLUMN IF NOT EXISTS domestic_regions TEXT;
ALTER TABLE "Trip" ADD COLUMN IF NOT EXISTS country_codes TEXT;

-- Make trip_type nullable in Trip table (can be confirmed once route is fully established)
ALTER TABLE "Trip" ALTER COLUMN trip_type DROP NOT NULL;

-- Add source activity and excursion references and series client key to TripPlanEntry
ALTER TABLE "TripPlanEntry" ADD COLUMN IF NOT EXISTS source_activity_id UUID;
ALTER TABLE "TripPlanEntry" ADD COLUMN IF NOT EXISTS source_excursion_id UUID;
ALTER TABLE "TripPlanEntry" ADD COLUMN IF NOT EXISTS series_client_key VARCHAR(100);
