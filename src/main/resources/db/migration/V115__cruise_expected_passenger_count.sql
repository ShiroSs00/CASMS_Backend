ALTER TABLE "Cruise"
    ADD COLUMN expected_passenger_count integer,
    ADD CONSTRAINT cruise_expected_passenger_count_positive CHECK (expected_passenger_count > 0);
