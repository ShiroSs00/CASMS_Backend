CREATE TABLE IF NOT EXISTS "ShipModelAssetBlob" (
    asset_id uuid PRIMARY KEY,
    model_key varchar(80) NOT NULL,
    asset_version varchar(80) NOT NULL,
    asset_path varchar(500) NOT NULL,
    blob_oid oid NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size >= 0),
    sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_ship_model_asset_blob_path UNIQUE (model_key, asset_version, asset_path)
);

CREATE INDEX IF NOT EXISTS idx_ship_model_asset_blob_bundle
    ON "ShipModelAssetBlob"(model_key, asset_version);

CREATE OR REPLACE FUNCTION casms_unlink_ship_model_asset_blob() RETURNS trigger AS $$
BEGIN
    PERFORM lo_unlink(OLD.blob_oid);
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ship_model_asset_blob_unlink ON "ShipModelAssetBlob";
CREATE TRIGGER trg_ship_model_asset_blob_unlink
    AFTER DELETE ON "ShipModelAssetBlob"
    FOR EACH ROW EXECUTE FUNCTION casms_unlink_ship_model_asset_blob();
