-- SOURCE ONLY. Extend the existing Booking/BookingPassenger hold lifecycle, never apply in this session.
-- NULL represents existing legacy records. No backfill or historical assignment changes.
ALTER TABLE "BookingPassenger"
    ADD COLUMN cabin_selection_mode varchar(20),
    ADD COLUMN cabin_group_key varchar(100),
    ADD COLUMN fare_offer_id uuid REFERENCES "DepartureFarePackage"(offer_id),
    ADD COLUMN fare_price_revision_id uuid,
    ADD COLUMN fare_template_version_id uuid,
    ADD COLUMN fare_cabin_category varchar(30),
    ADD COLUMN fare_selection_payload jsonb;
ALTER TABLE "BookingPassenger" ADD CONSTRAINT booking_passenger_cabin_mode
    CHECK (cabin_selection_mode IS NULL OR cabin_selection_mode IN ('PHYSICAL_CABIN','CATEGORY_HOLD'));
ALTER TABLE "BookingPassenger" ADD CONSTRAINT booking_passenger_fare_selection_complete CHECK (
    (fare_offer_id IS NULL AND fare_price_revision_id IS NULL AND fare_template_version_id IS NULL
        AND fare_cabin_category IS NULL AND fare_selection_payload IS NULL AND cabin_selection_mode IS DISTINCT FROM 'CATEGORY_HOLD')
    OR (fare_offer_id IS NOT NULL AND fare_price_revision_id IS NOT NULL AND fare_template_version_id IS NOT NULL
        AND fare_cabin_category IS NOT NULL AND fare_selection_payload IS NOT NULL AND cabin_group_key IS NOT NULL
        AND cabin_selection_mode IS NOT NULL
        AND cabin_selection_mode IN ('PHYSICAL_CABIN','CATEGORY_HOLD'))
);
ALTER TABLE "BookingPassenger" ADD CONSTRAINT booking_passenger_fare_price_identity
    FOREIGN KEY (fare_price_revision_id,fare_offer_id,fare_template_version_id)
    REFERENCES "DepartureFarePackagePrice"(price_revision_id,offer_id,template_version_id);
CREATE INDEX booking_passenger_fare_offer ON "BookingPassenger"(fare_offer_id);
CREATE INDEX booking_passenger_fare_price ON "BookingPassenger"(fare_price_revision_id);
CREATE INDEX booking_passenger_category_resource ON "BookingPassenger"(cabin_selection_mode,fare_cabin_category,booking_id);
