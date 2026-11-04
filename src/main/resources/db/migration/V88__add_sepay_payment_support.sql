-- V82: Add Sepay payment method to enum and create SepayTransaction table

ALTER TYPE "Payment_payment_method" ADD VALUE IF NOT EXISTS 'Sepay';

CREATE TABLE IF NOT EXISTS "SepayTransaction" (
    "id" uuid PRIMARY KEY NOT NULL,
    "sepay_id" bigint UNIQUE NOT NULL,
    "gateway" varchar(100),
    "transaction_date" timestamptz,
    "account_number" varchar(100),
    "transfer_type" varchar(20),
    "transfer_amount" decimal(19,4) NOT NULL,
    "accumulated" decimal(19,4),
    "code" varchar(100),
    "content" text,
    "reference_code" varchar(255),
    "description" text,
    "payment_id" uuid,
    "booking_id" uuid,
    "status" varchar(50) NOT NULL,
    "matched_at" timestamptz,
    "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "fk_sepay_payment" FOREIGN KEY ("payment_id") REFERENCES "Payment"("payment_id") ON DELETE SET NULL,
    CONSTRAINT "fk_sepay_booking" FOREIGN KEY ("booking_id") REFERENCES "Booking"("booking_id") ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS "idx_sepay_transaction_reference" ON "SepayTransaction"("reference_code");
CREATE INDEX IF NOT EXISTS "idx_sepay_transaction_payment_id" ON "SepayTransaction"("payment_id");
CREATE INDEX IF NOT EXISTS "idx_sepay_transaction_booking_id" ON "SepayTransaction"("booking_id");

