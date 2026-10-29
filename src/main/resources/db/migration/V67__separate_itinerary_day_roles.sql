-- Position in the schedule is independent of SEA_DAY / PORT_DAY.
ALTER TABLE "TripPlanDay" ADD COLUMN day_role VARCHAR(40) NOT NULL DEFAULT 'REGULAR';
ALTER TABLE "ItineraryDay" ADD COLUMN day_role VARCHAR(40) NOT NULL DEFAULT 'REGULAR';

UPDATE "TripPlanDay" d SET day_role = CASE
    WHEN t.duration_days = 1 THEN 'DEPARTURE_AND_ARRIVAL'
    WHEN d.day_number = 1 THEN 'DEPARTURE'
    WHEN d.day_number = t.duration_days THEN 'ARRIVAL'
    ELSE 'REGULAR' END
FROM "TripScheduleVersion" v JOIN "Trip" t ON t.trip_id = v.trip_id
WHERE d.version_id = v.version_id;

UPDATE "ItineraryDay" d SET day_role = CASE
    WHEN i.start_date = i.end_date THEN 'DEPARTURE_AND_ARRIVAL'
    WHEN d.day_number = 1 THEN 'DEPARTURE'
    WHEN d.date = i.end_date THEN 'ARRIVAL'
    ELSE 'REGULAR' END
FROM "Itinerary" i WHERE d.itinerary_id = i.itinerary_id;

ALTER TABLE "TripPlanDay" ADD CONSTRAINT ck_trip_day_role CHECK (day_role IN ('DEPARTURE','REGULAR','ARRIVAL','DEPARTURE_AND_ARRIVAL'));
ALTER TABLE "ItineraryDay" ADD CONSTRAINT ck_itinerary_day_role CHECK (day_role IN ('DEPARTURE','REGULAR','ARRIVAL','DEPARTURE_AND_ARRIVAL'));
