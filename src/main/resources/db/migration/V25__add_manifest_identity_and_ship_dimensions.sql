ALTER TABLE "Ship"
    ADD COLUMN manifest_model_id varchar(80),
    ADD COLUMN length_meters double precision,
    ADD COLUMN beam_meters double precision;

CREATE UNIQUE INDEX uk_ship_manifest_model_id
    ON "Ship"(manifest_model_id)
    WHERE manifest_model_id IS NOT NULL;

ALTER TABLE "Ship"
    ADD CONSTRAINT ck_ship_model_dimensions_positive
    CHECK ((length_meters IS NULL OR length_meters > 0)
       AND (beam_meters IS NULL OR beam_meters > 0));
