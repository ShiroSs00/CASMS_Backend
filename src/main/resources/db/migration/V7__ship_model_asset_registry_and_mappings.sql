-- Add the versioned asset registry fields used by the FE model loader.
ALTER TABLE "ShipModel" ADD COLUMN IF NOT EXISTS model_key varchar(80);
ALTER TABLE "ShipModel" ADD COLUMN IF NOT EXISTS asset_revision varchar(160);
ALTER TABLE "ShipModel" ADD COLUMN IF NOT EXISTS asset_version varchar(80);
ALTER TABLE "ShipModel" ALTER COLUMN version TYPE varchar(161);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ship_model_asset_key_version
    ON "ShipModel"(ship_id, model_key, asset_version)
    WHERE model_key IS NOT NULL AND asset_version IS NOT NULL;

-- READY is the existing immutable published state; the FE API exposes it as ACTIVE.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ship_model_single_active_asset
    ON "ShipModel"(ship_id)
    WHERE model_key IS NOT NULL AND status = 'READY';
