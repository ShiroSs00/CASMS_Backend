-- V54: Add guest_token to BookingQuote to support anonymous quote ownership and verified access
ALTER TABLE "BookingQuote" ADD COLUMN IF NOT EXISTS "guest_token" VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_booking_quote_guest_token ON "BookingQuote"("guest_token");
