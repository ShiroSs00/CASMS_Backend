-- Destination-first shore excursions. A Port is an access point and may serve several destinations.
CREATE TABLE "PortDestinationAccess" (
  port_id uuid NOT NULL REFERENCES "Port"(port_id) ON DELETE RESTRICT,
  destination_id uuid NOT NULL REFERENCES "Destination"(destination_id) ON DELETE RESTRICT,
  PRIMARY KEY (port_id, destination_id)
);

-- Preserve the established primary directory association as a served destination.
INSERT INTO "PortDestinationAccess" (port_id, destination_id)
SELECT port_id, destination_id FROM "Port" WHERE destination_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX "port_destination_access_destination_idx"
  ON "PortDestinationAccess" (destination_id, port_id);

-- A candidate is not operationally verified until the exact destination, access port,
-- supplier, transfer duration and call-return evidence have all been checked.
ALTER TABLE "OperationalSeedTour"
  ADD COLUMN verified_destination_id uuid REFERENCES "Destination"(destination_id),
  ADD COLUMN verified_cruise_stop_id uuid REFERENCES "CruiseStop"(cruise_stop_id),
  ADD COLUMN verified_travel_minutes integer,
  ADD COLUMN verified_tour_start_time timestamptz,
  ADD COLUMN verified_planned_return_time timestamptz,
  ADD CONSTRAINT operational_seed_tour_travel_minutes_check
    CHECK (verified_travel_minutes IS NULL OR verified_travel_minutes >= 0);

DROP VIEW "OperationalVerifiedTour";
CREATE VIEW "OperationalVerifiedTour" AS
 SELECT t.tour_code,t.tour_name,t.port_region,t.duration_minutes,t.capacity,
        t.reference_price_vnd,t.verified_port_id,t.verified_destination_id,t.verified_cruise_stop_id,
        t.verified_supplier_id,t.verified_travel_minutes,t.verified_tour_start_time,t.verified_planned_return_time,
        t.reviewed_by,t.reviewed_at
 FROM "OperationalSeedTour" t
 JOIN "Port" p ON p.port_id=t.verified_port_id
 JOIN "PortDestinationAccess" a ON a.port_id=p.port_id AND a.destination_id=t.verified_destination_id
 JOIN "Destination" d ON d.destination_id=a.destination_id AND d.status='ACTIVE'
 JOIN "CruiseStop" cs ON cs.cruise_stop_id=t.verified_cruise_stop_id AND cs.port_id=p.port_id
 JOIN "Supplier" s ON s.supplier_id=t.verified_supplier_id
 WHERE t.status='VERIFIED' AND t.verified_destination_id IS NOT NULL
   AND t.verified_cruise_stop_id IS NOT NULL AND t.verified_travel_minutes IS NOT NULL
   AND t.verified_tour_start_time IS NOT NULL AND t.verified_planned_return_time IS NOT NULL
   AND cs.status::text NOT IN ('OMITTED','SUBSTITUTED')
   AND (cs.destination_id=t.verified_destination_id OR EXISTS(
       SELECT 1 FROM "CruiseDestinationVisit" v
        WHERE v.cruise_stop_id=cs.cruise_stop_id AND v.destination_id=t.verified_destination_id))
   AND p.status::text='ACTIVE' AND trim(p.country_code)='VN'
   AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
   AND s.status::text='ACTIVE' AND s.verification_status::text='VERIFIED';
