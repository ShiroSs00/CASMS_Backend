-- Additive. No conversion of legacy destination_id; no guessed historical facts.
CREATE TABLE "DestinationVisit" (
 visit_id uuid PRIMARY KEY,stop_id uuid NOT NULL REFERENCES "TripPlanStop"(stop_id),destination_id uuid NOT NULL REFERENCES "Destination"(destination_id),
 client_key varchar(100) NOT NULL,sequence integer NOT NULL CHECK(sequence>0),destination_name_snapshot varchar(255),UNIQUE(stop_id,client_key)
);
CREATE TABLE "CruiseDestinationVisit" (
 visit_id uuid PRIMARY KEY,cruise_stop_id uuid NOT NULL REFERENCES "CruiseStop"(cruise_stop_id),destination_id uuid NOT NULL REFERENCES "Destination"(destination_id),
 source_visit_id uuid NOT NULL REFERENCES "DestinationVisit"(visit_id),sequence integer NOT NULL CHECK(sequence>0),destination_name_snapshot varchar(255) NOT NULL
);
CREATE INDEX cruise_visit_parent_idx ON "CruiseDestinationVisit"(cruise_stop_id);
ALTER TABLE "TripPlanStop" ADD COLUMN port_facts jsonb;
ALTER TABLE "TripPlanDay" ADD COLUMN departure_port_facts jsonb;
ALTER TABLE "CruiseStop" ADD COLUMN port_facts jsonb,ADD COLUMN source_stop_id uuid REFERENCES "TripPlanStop"(stop_id),ADD COLUMN port_window_evidence jsonb;
ALTER TABLE "Itinerary" ADD COLUMN departure_port_facts jsonb,ADD COLUMN return_port_facts jsonb;
