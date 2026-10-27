-- BE-10: Add hold_expires_at and index for hold TTL and inventory management
ALTER TABLE "Booking" ADD COLUMN IF NOT EXISTS "hold_expires_at" TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS "idx_booking_hold_status_expires" ON "Booking"("booking_status", "hold_expires_at");
