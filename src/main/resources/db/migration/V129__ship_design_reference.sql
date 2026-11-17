DROP INDEX uq_ship_imported_yard_hull_code;

ALTER TABLE "Ship"
    DROP COLUMN design_metadata_imported,
    ADD COLUMN design_model_key varchar(80),
    ADD COLUMN design_asset_version varchar(80),
    ADD COLUMN design_manifest_path varchar(500) GENERATED ALWAYS AS ('manifest.json'::varchar(500)) STORED,
    ADD CONSTRAINT ck_ship_design_reference_pair
        CHECK ((design_model_key IS NULL) = (design_asset_version IS NULL)),
    ADD CONSTRAINT fk_ship_design_manifest
        FOREIGN KEY (design_model_key, design_asset_version, design_manifest_path)
        REFERENCES "ShipModelAssetBlob" (model_key, asset_version, asset_path);

-- Use the oldest retained published model of the original design for existing vessels.
-- Never infer a vessel's version from the ShipType's current version.
WITH existing_pins AS (
    SELECT DISTINCT ON (s.ship_id) s.ship_id, m.model_key, m.asset_version
    FROM "Ship" s
    JOIN "ShipModel" m ON m.ship_id = s.ship_id AND m.model_key = s.manifest_model_id
    JOIN "ShipModelAssetBlob" a ON a.model_key = m.model_key
        AND a.asset_version = m.asset_version AND a.asset_path = 'manifest.json'
    WHERE s.ship_type_id IS NOT NULL AND m.status IN ('READY', 'ARCHIVED')
    ORDER BY s.ship_id, m.created_at, m.ship_model_id
)
UPDATE "Ship" s
SET design_model_key = p.model_key, design_asset_version = p.asset_version
FROM existing_pins p
WHERE s.ship_id = p.ship_id;
