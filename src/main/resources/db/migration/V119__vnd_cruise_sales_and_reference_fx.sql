ALTER TABLE "Cruise" ADD COLUMN sale_currency VARCHAR(3) NOT NULL DEFAULT 'USD'
    CHECK (sale_currency IN ('VND', 'USD'));

ALTER TABLE "DepartureFarePackagePrice" DROP CONSTRAINT "DepartureFarePackagePrice_currency_code_check";
ALTER TABLE "DepartureFarePackagePrice" ADD CHECK (
    currency_code = 'USD' OR (currency_code = 'VND' AND amount = trunc(amount)));
ALTER TABLE "BookingFareSnapshot" DROP CONSTRAINT "BookingFareSnapshot_currency_code_check";
ALTER TABLE "BookingFareSnapshot" ADD CHECK (
    currency_code = 'USD' OR (currency_code = 'VND' AND amount = trunc(amount)));

CREATE TABLE "UsdDisplayRate" (
    effective_on DATE PRIMARY KEY,
    vnd_per_usd NUMERIC(12,4) NOT NULL CHECK (vnd_per_usd > 0),
    source VARCHAR(160) NOT NULL CHECK (length(trim(source)) > 0),
    published_by UUID NOT NULL REFERENCES "User"(user_id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
