-- CR-ASM-G1: approved source only. Never apply during the coding session.
-- Keep all existing rows/identities; operational records retain required timing/capacity.
ALTER TABLE "OnboardActivity" ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE "OnboardActivity" ALTER COLUMN end_time DROP NOT NULL;
ALTER TABLE "OnboardActivity" ADD CONSTRAINT onboard_actual_times_required
    CHECK (cruise_id IS NULL OR (start_time IS NOT NULL AND end_time IS NOT NULL));
ALTER TABLE "ShoreExcursion" ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE "ShoreExcursion" ALTER COLUMN planned_return_time DROP NOT NULL;
ALTER TABLE "ShoreExcursion" ALTER COLUMN maximum_capacity DROP NOT NULL;
ALTER TABLE "ShoreExcursion" ADD CONSTRAINT excursion_actual_fields_required
    CHECK (cruise_stop_id IS NULL OR
        (start_time IS NOT NULL AND planned_return_time IS NOT NULL AND maximum_capacity IS NOT NULL));
