-- V83: Relax Payment check constraint to allow standalone quick payments without booking or onboard_account
ALTER TABLE "Payment" DROP CONSTRAINT IF EXISTS "Payment_check";

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT oid, conname
        FROM pg_constraint
        WHERE conrelid = '"Payment"'::regclass AND contype = 'c'
    ) LOOP
        IF pg_get_constraintdef(r.oid) LIKE '%booking_id%' THEN
            EXECUTE 'ALTER TABLE "Payment" DROP CONSTRAINT ' || quote_ident(r.conname);
        END IF;
    END LOOP;
END $$;

ALTER TABLE "Payment" DROP CONSTRAINT IF EXISTS "chk_payment_booking_onboard_excl";
ALTER TABLE "Payment" ADD CONSTRAINT "chk_payment_booking_onboard_excl"
    CHECK (NOT (booking_id IS NOT NULL AND onboard_account_id IS NOT NULL));
