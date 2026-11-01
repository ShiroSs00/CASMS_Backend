-- DEC-PORT-02: locality provenance on existing local Ports, without guessed historical backfill.
ALTER TABLE "Port" ADD COLUMN coordinate_source jsonb;
-- Remove fixed-scale rounding so selected coordinates are retained as submitted.
ALTER TABLE "Port" ALTER COLUMN latitude TYPE numeric;
ALTER TABLE "Port" ALTER COLUMN longitude TYPE numeric;
-- No new ID, external-location uniqueness, backfill, guessed provenance or coordinate-value rewrite.
