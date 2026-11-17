ALTER TABLE "FarePackageTemplateVersion"
    ADD COLUMN source_ship_id uuid REFERENCES "Ship"(ship_id);

CREATE INDEX fare_template_published_source_ship
    ON "FarePackageTemplateVersion"(source_ship_id)
    WHERE status = 'PUBLISHED';

ALTER TABLE "CruisePackageLogicalCategory"
    ADD COLUMN source_ship_id uuid REFERENCES "Ship"(ship_id),
    ADD COLUMN source_cabin_type varchar(30),
    ADD CONSTRAINT cruise_package_category_source_pair CHECK (
        (source_ship_id IS NULL AND source_cabin_type IS NULL)
        OR (source_ship_id IS NOT NULL AND source_cabin_type IS NOT NULL
            AND source_cabin_type IN ('INTERIOR','OCEAN_VIEW','BALCONY','SUITE'))
    ),
    ADD CONSTRAINT cruise_package_category_source_unique UNIQUE (source_ship_id, source_cabin_type);
