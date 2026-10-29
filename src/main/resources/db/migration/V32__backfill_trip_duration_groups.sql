-- V32__backfill_trip_duration_groups.sql
-- Backfill duration_group according to duration_days
-- SHORT: 1-2 days, LONG: 3-5 days

UPDATE "Trip"
SET duration_group = 'LONG'
WHERE duration_days >= 3 AND (duration_group IS NULL OR duration_group = 'SHORT');

UPDATE "Trip"
SET duration_group = 'SHORT'
WHERE duration_days < 3 AND (duration_group IS NULL OR duration_group = 'LONG');

-- Ensure duration_nights is set if null (duration_days - 1, min 0)
UPDATE "Trip"
SET duration_nights = GREATEST(0, duration_days - 1)
WHERE duration_nights IS NULL;

-- Add submitted_at column to TripScheduleVersion
ALTER TABLE "TripScheduleVersion" ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;

-- Backfill submitted_at for existing IN_REVIEW versions
UPDATE "TripScheduleVersion"
SET submitted_at = updated_at
WHERE status = 'IN_REVIEW' AND submitted_at IS NULL;
