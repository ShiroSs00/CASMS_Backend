-- V37__link_cruise_to_trip_version_and_revision.sql
-- Link Cruise to source Trip and source TripScheduleVersion (D01, C03, C15)
-- Add revision for optimistic concurrency control (D09, E08)

ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS source_trip_id UUID REFERENCES "Trip"(trip_id);
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS source_version_id UUID REFERENCES "TripScheduleVersion"(version_id);
ALTER TABLE "Cruise" ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_cruise_source_trip ON "Cruise"(source_trip_id);
CREATE INDEX IF NOT EXISTS idx_cruise_source_version ON "Cruise"(source_version_id);
