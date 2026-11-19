ALTER TABLE "Trip" DROP CONSTRAINT chk_trip_departure_window;

ALTER TABLE "Trip"
    ALTER COLUMN earliest_departure TYPE TIME WITHOUT TIME ZONE
        USING date_trunc('minute', earliest_departure AT TIME ZONE 'Asia/Ho_Chi_Minh')::time,
    ALTER COLUMN latest_departure TYPE TIME WITHOUT TIME ZONE
        USING date_trunc('minute', latest_departure AT TIME ZONE 'Asia/Ho_Chi_Minh')::time;

-- A legacy interval spanning dates could have its local clock endpoints in reverse order.
UPDATE "Trip"
SET (earliest_departure, latest_departure) = (latest_departure, earliest_departure)
WHERE earliest_departure > latest_departure;

ALTER TABLE "Trip"
    ADD CONSTRAINT chk_trip_departure_window CHECK (
        (earliest_departure IS NULL AND latest_departure IS NULL)
        OR (earliest_departure IS NOT NULL AND latest_departure IS NOT NULL
            AND earliest_departure <= latest_departure
            AND EXTRACT(SECOND FROM earliest_departure) = 0
            AND EXTRACT(SECOND FROM latest_departure) = 0)
    );
