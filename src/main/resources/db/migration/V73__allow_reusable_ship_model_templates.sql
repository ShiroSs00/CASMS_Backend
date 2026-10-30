-- A manifest identifies a reusable model template, not a unique physical ship.
DROP INDEX uk_ship_manifest_model_id;
CREATE INDEX idx_ship_manifest_model_id ON "Ship"(manifest_model_id) WHERE manifest_model_id IS NOT NULL;
-- Content-based upload limits query the existing asset hashes, including older uploads.
CREATE INDEX idx_ship_model_asset_blob_sha256 ON "ShipModelAssetBlob"(sha256);
