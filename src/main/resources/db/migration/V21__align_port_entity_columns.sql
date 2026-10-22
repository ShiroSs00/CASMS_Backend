-- Port already exposes these optional fields in the JPA entity. Add them without changing existing rows.
ALTER TABLE "Port"
  ADD COLUMN IF NOT EXISTS "destination_id" uuid,
  ADD COLUMN IF NOT EXISTS "description" varchar(1000);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint fk
    WHERE fk.contype = 'f'
      AND fk.conrelid = '"Port"'::regclass
      AND fk.confrelid = '"Destination"'::regclass
      AND fk.conkey = ARRAY[(
        SELECT attnum
        FROM pg_attribute
        WHERE attrelid = '"Port"'::regclass
          AND attname = 'destination_id'
          AND NOT attisdropped
      )]::smallint[]
      AND fk.confkey = ARRAY[(
        SELECT attnum
        FROM pg_attribute
        WHERE attrelid = '"Destination"'::regclass
          AND attname = 'destination_id'
          AND NOT attisdropped
      )]::smallint[]
  ) THEN
    ALTER TABLE "Port"
      ADD CONSTRAINT "port_destination_fk"
      FOREIGN KEY ("destination_id") REFERENCES "Destination"("destination_id");
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS "port_destination_idx" ON "Port" ("destination_id");
