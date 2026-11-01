-- ITN-04: additive snapshot metadata only. No historical backfill or legacy mutation.
ALTER TABLE "Itinerary" ADD COLUMN source_duration_nights integer;
ALTER TABLE "Itinerary" ADD COLUMN source_content_hash varchar(64);
ALTER TABLE "OnboardActivity" ADD COLUMN meeting_point varchar(255);
ALTER TABLE "ShoreExcursion" ADD COLUMN location_text varchar(255);
ALTER TABLE "ShoreExcursion" ADD COLUMN notes text;
ALTER TABLE "ShoreExcursion" ADD COLUMN destination_id uuid REFERENCES "Destination"(destination_id);
ALTER TABLE "ItineraryDay" ADD COLUMN source_day_description text;
