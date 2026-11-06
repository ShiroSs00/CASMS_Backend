ALTER TABLE "Ship"
    ADD COLUMN manufacturer_name varchar(150),
    ADD COLUMN origin varchar(100),
    ADD COLUMN production_year integer,
    ADD COLUMN engine_type varchar(100),
    ADD CONSTRAINT ck_ship_production_year CHECK (production_year BETWEEN 1 AND 9999);
