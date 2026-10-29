-- Departure metadata belongs to day 1, independently of sea/port calls.
ALTER TABLE "TripPlanDay" ADD COLUMN departure_port_id UUID REFERENCES "Port"(port_id);
ALTER TABLE "TripPlanDay" ADD COLUMN planned_departure_time TIME;
ALTER TABLE "TripPlanDay" ADD CONSTRAINT ck_departure_details_day_one
    CHECK (day_number = 1 OR (departure_port_id IS NULL AND planned_departure_time IS NULL));
