ALTER TABLE "Ship"
    ADD COLUMN design_metadata_imported boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_ship_imported_yard_hull_code
    ON "Ship" (lower(operational_profile ->> 'shipyard'),
               lower(operational_profile ->> 'manufacturerShipCode'))
    WHERE design_metadata_imported;
