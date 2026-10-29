-- Materialized draft activities must retain unknown capacity instead of inventing seats.
-- Operational activation and Cruise Plan submission validate positive configured capacity.
ALTER TABLE "OnboardActivity" ALTER COLUMN capacity DROP NOT NULL;
