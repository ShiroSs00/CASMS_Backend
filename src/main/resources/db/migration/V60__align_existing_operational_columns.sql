-- Existing entity fields missing from the migration chain; additive, no historical rows rewritten.
ALTER TABLE "ImpactNotificationOutbox" ADD COLUMN IF NOT EXISTS "delivery_channel" varchar(30);
ALTER TABLE "ImpactNotificationOutbox" ADD COLUMN IF NOT EXISTS "delivery_semantics" varchar(255);
ALTER TABLE "ImpactNotificationOutbox" ADD COLUMN IF NOT EXISTS "in_app_delivered_at" timestamptz;
ALTER TABLE "CruiseStop" ADD COLUMN IF NOT EXISTS "port_agency_name" varchar(200);
ALTER TABLE "CruiseStop" ADD COLUMN IF NOT EXISTS "berth_identifier" varchar(100);
