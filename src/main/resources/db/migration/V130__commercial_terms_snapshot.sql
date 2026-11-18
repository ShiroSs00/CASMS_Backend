ALTER TABLE "BookingQuote"
    ADD COLUMN commercial_terms_fingerprint varchar(64);

ALTER TABLE "Booking"
    ADD COLUMN commercial_terms_snapshot jsonb,
    ADD CONSTRAINT booking_commercial_terms_snapshot_object
        CHECK (jsonb_typeof(commercial_terms_snapshot) = 'object');

CREATE FUNCTION protect_booking_commercial_terms_snapshot() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.commercial_terms_snapshot IS DISTINCT FROM OLD.commercial_terms_snapshot THEN
        RAISE EXCEPTION 'Booking commercial terms snapshot is immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER booking_commercial_terms_snapshot_immutable
    BEFORE UPDATE OF commercial_terms_snapshot ON "Booking"
    FOR EACH ROW EXECUTE FUNCTION protect_booking_commercial_terms_snapshot();
