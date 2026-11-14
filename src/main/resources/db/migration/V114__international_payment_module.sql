-- V114: International Cruise Payment Module (Multi-Currency, Tokenized Payment Methods,
--       Gateway Abstraction Attempts/Webhooks, Offline POS Sync Logs, End-of-Cruise Settlement)

-- 1. Extend existing PostgreSQL Enum types safely
ALTER TYPE "OnboardAccount_status" ADD VALUE IF NOT EXISTS 'SUSPENDED';

ALTER TYPE "OnboardTransaction_transaction_type" ADD VALUE IF NOT EXISTS 'REVERSAL';
ALTER TYPE "OnboardTransaction_status" ADD VALUE IF NOT EXISTS 'VOIDED';
ALTER TYPE "OnboardTransaction_sync_status" ADD VALUE IF NOT EXISTS 'NOT_SYNCED';
ALTER TYPE "OnboardTransaction_sync_status" ADD VALUE IF NOT EXISTS 'SYNCING';
ALTER TYPE "OnboardTransaction_sync_status" ADD VALUE IF NOT EXISTS 'SYNC_FAILED';
ALTER TYPE "OnboardTransaction_sync_status" ADD VALUE IF NOT EXISTS 'CONFLICT';

ALTER TYPE "Payment_payment_method" ADD VALUE IF NOT EXISTS 'CreditCard';
ALTER TYPE "Payment_payment_method" ADD VALUE IF NOT EXISTS 'TokenizedCard';

ALTER TYPE "Payment_status" ADD VALUE IF NOT EXISTS 'Initiated';
ALTER TYPE "Payment_status" ADD VALUE IF NOT EXISTS 'Authorized';
ALTER TYPE "Payment_status" ADD VALUE IF NOT EXISTS 'Captured';
ALTER TYPE "Payment_status" ADD VALUE IF NOT EXISTS 'PartiallyRefunded';
ALTER TYPE "Payment_status" ADD VALUE IF NOT EXISTS 'Refunded';

ALTER TYPE "FinalSettlement_status" ADD VALUE IF NOT EXISTS 'Processing';
ALTER TYPE "FinalSettlement_status" ADD VALUE IF NOT EXISTS 'Completed';
ALTER TYPE "FinalSettlement_status" ADD VALUE IF NOT EXISTS 'PartiallyPaid';
ALTER TYPE "FinalSettlement_status" ADD VALUE IF NOT EXISTS 'Failed';
ALTER TYPE "FinalSettlement_status" ADD VALUE IF NOT EXISTS 'Disputed';

-- 2. Add onboard base currency to Cruise (default USD)
ALTER TABLE "Cruise"
    ADD COLUMN IF NOT EXISTS "base_currency" char(3) NOT NULL DEFAULT 'USD';

-- 3. Extend OnboardAccount with base_currency, primary_passenger_id, pre_authorized_amount
ALTER TABLE "OnboardAccount"
    ADD COLUMN IF NOT EXISTS "base_currency" char(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS "primary_passenger_id" uuid REFERENCES "Passenger"("passenger_id"),
    ADD COLUMN IF NOT EXISTS "pre_authorized_amount" decimal(19,4) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS "idx_onboard_account_cruise_passenger"
    ON "OnboardAccount"("cruise_id", "primary_passenger_id");

-- 4. Extend OnboardTransaction for offline-first POS & idempotency
ALTER TABLE "OnboardTransaction"
    ADD COLUMN IF NOT EXISTS "pos_terminal_id" uuid REFERENCES "PosTerminal"("terminal_id"),
    ADD COLUMN IF NOT EXISTS "device_reference" varchar(100),
    ADD COLUMN IF NOT EXISTS "idempotency_key" varchar(100),
    ADD COLUMN IF NOT EXISTS "service_reference" varchar(255),
    ADD COLUMN IF NOT EXISTS "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_onboard_tx_idempotency_key"
    ON "OnboardTransaction"("idempotency_key")
    WHERE "idempotency_key" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "idx_onboard_tx_account_status"
    ON "OnboardTransaction"("onboard_account_id", "status", "transaction_type");

-- 5. Tokenized Payment Method table (Never stores raw PAN or CVV)
CREATE TABLE IF NOT EXISTS "RegisteredPaymentMethod" (
    "payment_method_id" uuid PRIMARY KEY NOT NULL,
    "passenger_id" uuid NOT NULL REFERENCES "Passenger"("passenger_id"),
    "provider" varchar(50) NOT NULL,
    "payment_token" varchar(255) NOT NULL UNIQUE,
    "provider_customer_reference" varchar(255),
    "card_brand" varchar(30) NOT NULL,
    "card_last4" char(4) NOT NULL,
    "card_holder_name" varchar(150),
    "expiry_month" int NOT NULL CHECK ("expiry_month" BETWEEN 1 AND 12),
    "expiry_year" int NOT NULL CHECK ("expiry_year" >= 2024),
    "issuing_country" char(2),
    "issuing_currency" char(3) NOT NULL DEFAULT 'USD',
    "is_default" boolean NOT NULL DEFAULT false,
    "status" varchar(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK ("status" IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS "idx_registered_pm_passenger"
    ON "RegisteredPaymentMethod"("passenger_id", "status");

-- 6. Extend Payment for Gateway Tokenization, Pre-Auth/Capture & Multi-Currency Conversion
ALTER TABLE "Payment"
    ADD COLUMN IF NOT EXISTS "registered_payment_method_id" uuid REFERENCES "RegisteredPaymentMethod"("payment_method_id"),
    ADD COLUMN IF NOT EXISTS "provider" varchar(50),
    ADD COLUMN IF NOT EXISTS "payment_token" varchar(255),
    ADD COLUMN IF NOT EXISTS "authorized_amount" decimal(19,4),
    ADD COLUMN IF NOT EXISTS "captured_amount" decimal(19,4),
    ADD COLUMN IF NOT EXISTS "refunded_amount" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "original_amount" decimal(19,4),
    ADD COLUMN IF NOT EXISTS "original_currency" char(3),
    ADD COLUMN IF NOT EXISTS "converted_amount" decimal(19,4),
    ADD COLUMN IF NOT EXISTS "settlement_currency" char(3),
    ADD COLUMN IF NOT EXISTS "exchange_rate_applied" decimal(19,6),
    ADD COLUMN IF NOT EXISTS "exchange_rate_source" varchar(100),
    ADD COLUMN IF NOT EXISTS "exchange_rate_timestamp" timestamptz,
    ADD COLUMN IF NOT EXISTS "authorized_at" timestamptz,
    ADD COLUMN IF NOT EXISTS "captured_at" timestamptz,
    ADD COLUMN IF NOT EXISTS "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 7. PaymentAttempt audit table
CREATE TABLE IF NOT EXISTS "PaymentAttempt" (
    "attempt_id" uuid PRIMARY KEY NOT NULL,
    "payment_id" uuid NOT NULL REFERENCES "Payment"("payment_id") ON DELETE CASCADE,
    "attempt_no" int NOT NULL CHECK ("attempt_no" > 0),
    "operation" varchar(40) NOT NULL
        CHECK ("operation" IN ('AUTHORIZE', 'CAPTURE', 'CHARGE', 'REFUND', 'VOID', 'STATUS_QUERY')),
    "provider" varchar(50) NOT NULL,
    "provider_transaction_id" varchar(255),
    "amount" decimal(19,4) NOT NULL CHECK ("amount" > 0),
    "currency" char(3) NOT NULL,
    "idempotency_key" varchar(255) NOT NULL,
    "status" varchar(30) NOT NULL
        CHECK ("status" IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'UNCERTAIN')),
    "error_code" varchar(100),
    "error_message" text,
    "requested_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completed_at" timestamptz
);

CREATE INDEX IF NOT EXISTS "idx_payment_attempt_payment"
    ON "PaymentAttempt"("payment_id", "attempt_no");

-- 8. PaymentWebhookEvent table
CREATE TABLE IF NOT EXISTS "PaymentWebhookEvent" (
    "webhook_event_id" uuid PRIMARY KEY NOT NULL,
    "provider" varchar(50) NOT NULL,
    "external_event_id" varchar(255) NOT NULL UNIQUE,
    "event_type" varchar(100) NOT NULL,
    "payment_id" uuid REFERENCES "Payment"("payment_id") ON DELETE SET NULL,
    "signature_verified" boolean NOT NULL DEFAULT false,
    "processing_status" varchar(30) NOT NULL
        CHECK ("processing_status" IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')),
    "payload_json" text NOT NULL,
    "error_message" text,
    "received_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "processed_at" timestamptz
);

CREATE INDEX IF NOT EXISTS "idx_webhook_event_payment"
    ON "PaymentWebhookEvent"("payment_id");

-- 9. CurrencyExchangeRate table & initial Asian cruise currencies seed
CREATE TABLE IF NOT EXISTS "CurrencyExchangeRate" (
    "exchange_rate_id" uuid PRIMARY KEY NOT NULL,
    "base_currency" char(3) NOT NULL,
    "target_currency" char(3) NOT NULL,
    "rate" decimal(19,6) NOT NULL CHECK ("rate" > 0),
    "source" varchar(100) NOT NULL,
    "effective_from" timestamptz NOT NULL,
    "effective_to" timestamptz,
    "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS "idx_fx_rate_lookup"
    ON "CurrencyExchangeRate"("base_currency", "target_currency", "effective_from" DESC);

INSERT INTO "CurrencyExchangeRate" ("exchange_rate_id", "base_currency", "target_currency", "rate", "source", "effective_from")
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'USD', 'USD', 1.000000, 'CRUISE_TREASURY_OFFICIAL', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000002', 'USD', 'SGD', 1.345000, 'MAS_REFERENCE_FEED', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000003', 'USD', 'MYR', 4.420000, 'BNM_REFERENCE_FEED', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000004', 'USD', 'THB', 34.800000, 'BOT_REFERENCE_FEED', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000005', 'USD', 'JPY', 153.500000, 'BOJ_REFERENCE_FEED', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000006', 'USD', 'HKD', 7.785000, 'HKMA_REFERENCE_FEED', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000007', 'USD', 'VND', 25400.000000, 'VIETCOMBANK_OFFICIAL', '2026-01-01T00:00:00Z'),
    ('a1000000-0000-0000-0000-000000000008', 'USD', 'EUR', 0.920000, 'ECB_REFERENCE_FEED', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;

-- 10. OfflineSyncLog table
CREATE TABLE IF NOT EXISTS "OfflineSyncLog" (
    "sync_log_id" uuid PRIMARY KEY NOT NULL,
    "batch_id" uuid NOT NULL,
    "pos_terminal_id" uuid REFERENCES "PosTerminal"("terminal_id"),
    "device_reference" varchar(100),
    "transaction_id" uuid REFERENCES "OnboardTransaction"("transaction_id") ON DELETE SET NULL,
    "onboard_account_id" uuid REFERENCES "OnboardAccount"("onboard_account_id") ON DELETE SET NULL,
    "idempotency_key" varchar(100) NOT NULL,
    "sync_outcome" varchar(30) NOT NULL
        CHECK ("sync_outcome" IN ('SYNCED', 'DUPLICATE_IGNORED', 'CONFLICT', 'SYNC_FAILED')),
    "conflict_code" varchar(100),
    "error_detail" text,
    "occurrence_time" timestamptz NOT NULL,
    "synced_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS "idx_offline_sync_log_batch"
    ON "OfflineSyncLog"("batch_id");
CREATE INDEX IF NOT EXISTS "idx_offline_sync_log_account"
    ON "OfflineSyncLog"("onboard_account_id", "sync_outcome");

-- 11. Extend FinalSettlement & create SettlementItem table
ALTER TABLE "FinalSettlement"
    ALTER COLUMN "final_invoice_id" DROP NOT NULL;

ALTER TABLE "FinalSettlement"
    ADD COLUMN IF NOT EXISTS "base_currency" char(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS "total_charges_snapshot" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "total_refunds_snapshot" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "total_payments_snapshot" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "paid_amount" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "outstanding_balance_snapshot" decimal(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "notes" text,
    ADD COLUMN IF NOT EXISTS "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS "SettlementItem" (
    "settlement_item_id" uuid PRIMARY KEY NOT NULL,
    "final_settlement_id" uuid NOT NULL REFERENCES "FinalSettlement"("final_settlement_id") ON DELETE CASCADE,
    "source_transaction_id" uuid REFERENCES "OnboardTransaction"("transaction_id"),
    "source_payment_id" uuid REFERENCES "Payment"("payment_id"),
    "item_type" varchar(40) NOT NULL
        CHECK ("item_type" IN ('CHARGE', 'REFUND', 'PAYMENT', 'ADJUSTMENT', 'REVERSAL')),
    "description" varchar(500) NOT NULL,
    "amount" decimal(19,4) NOT NULL,
    "currency" char(3) NOT NULL DEFAULT 'USD',
    "occurred_at" timestamptz NOT NULL,
    "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS "idx_settlement_item_settlement"
    ON "SettlementItem"("final_settlement_id");

