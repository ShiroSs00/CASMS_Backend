-- Port already exposes these optional fields in the JPA entity. Add them without changing existing rows.
ALTER TABLE "Port"
  ADD COLUMN "destination_id" uuid,
  ADD COLUMN "description" varchar(1000);

ALTER TABLE "Port"
  ADD CONSTRAINT "port_destination_fk"
  FOREIGN KEY ("destination_id") REFERENCES "Destination"("destination_id");

CREATE INDEX "port_destination_idx" ON "Port" ("destination_id");
