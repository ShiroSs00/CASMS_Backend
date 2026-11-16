ALTER TABLE "Trip"
    ADD COLUMN earliest_departure TIMESTAMP WITH TIME ZONE,
    ADD COLUMN latest_departure TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_trip_departure_window CHECK (
        (earliest_departure IS NULL AND latest_departure IS NULL)
        OR (earliest_departure IS NOT NULL AND latest_departure IS NOT NULL
            AND earliest_departure <= latest_departure)
    );
