ALTER TABLE "TourPackage" ADD COLUMN owner_cruise_id uuid REFERENCES "Cruise"(cruise_id);
CREATE INDEX tour_package_owner_idx ON "TourPackage"(owner_cruise_id);
