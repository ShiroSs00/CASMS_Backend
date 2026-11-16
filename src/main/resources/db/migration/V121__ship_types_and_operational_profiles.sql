CREATE TABLE "ShipType" (
    ship_type_id uuid PRIMARY KEY,
    type_code varchar(80) NOT NULL UNIQUE,
    type_name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    model_key varchar(80) NOT NULL,
    asset_version varchar(80) NOT NULL,
    manifest_path varchar(500) NOT NULL DEFAULT 'manifest.json' CHECK (manifest_path = 'manifest.json'),
    manufacturer_name varchar(150) NOT NULL,
    engine_type varchar(100) NOT NULL,
    total_capacity integer NOT NULL CHECK (total_capacity > 0),
    total_decks integer NOT NULL CHECK (total_decks > 0),
    length_meters double precision NOT NULL CHECK (length_meters > 0),
    beam_meters double precision NOT NULL CHECK (beam_meters > 0),
    CONSTRAINT fk_ship_type_model_manifest FOREIGN KEY (model_key, asset_version, manifest_path)
        REFERENCES "ShipModelAssetBlob" (model_key, asset_version, asset_path)
);

ALTER TABLE "Ship"
    ADD COLUMN ship_type_id uuid REFERENCES "ShipType"(ship_type_id),
    ADD COLUMN created_by_ship_manager_id uuid REFERENCES "User"(user_id) ON DELETE SET NULL,
    ADD COLUMN operational_profile jsonb;

CREATE INDEX idx_ship_ship_type ON "Ship"(ship_type_id);
CREATE INDEX idx_ship_created_by_ship_manager ON "Ship"(created_by_ship_manager_id);

CREATE UNIQUE INDEX uk_ship_profile_imo
    ON "Ship" ((operational_profile ->> 'imoNumber'));

CREATE UNIQUE INDEX uk_ship_profile_registration
    ON "Ship" ((operational_profile ->> 'registrationCountry'),
               (operational_profile ->> 'registrationNumber'));
