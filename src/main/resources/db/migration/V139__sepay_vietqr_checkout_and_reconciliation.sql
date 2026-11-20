-- V139: Immutable VietQR checkout snapshots, concurrency guard, and Finance SePay reconciliation audit

ALTER TABLE "Payment"
    ADD COLUMN IF NOT EXISTS "checkout_payment_type" varchar(30),
    ADD COLUMN IF NOT EXISTS "qr_code_url" text,
    ADD COLUMN IF NOT EXISTS "qr_bank_name" varchar(100),
    ADD COLUMN IF NOT EXISTS "qr_account_number" varchar(100),
    ADD COLUMN IF NOT EXISTS "qr_account_holder_name" varchar(255),
    ADD COLUMN IF NOT EXISTS "qr_expires_at" timestamptz;

-- Ensure at most one active Pending VietQR booking checkout exists per booking at any time
CREATE UNIQUE INDEX IF NOT EXISTS "uk_payment_active_sepay_vietqr_booking"
    ON "Payment" ("booking_id")
    WHERE "booking_id" IS NOT NULL
      AND "status" = 'Pending'
      AND "payment_method" = 'Sepay'
      AND "provider" = 'SEPAY_VIETQR';

ALTER TABLE "SepayTransaction"
    ADD COLUMN IF NOT EXISTS "currency" varchar(10) DEFAULT 'VND',
    ADD COLUMN IF NOT EXISTS "expected_amount" numeric(19, 4),
    ADD COLUMN IF NOT EXISTS "exception_code" varchar(64),
    ADD COLUMN IF NOT EXISTS "exception_reason" text,
    ADD COLUMN IF NOT EXISTS "reconciliation_status" varchar(40) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS "reconciliation_action" varchar(40),
    ADD COLUMN IF NOT EXISTS "reconciliation_reason" text,
    ADD COLUMN IF NOT EXISTS "reconciliation_evidence_ref" varchar(255),
    ADD COLUMN IF NOT EXISTS "reconciled_by_user_id" uuid,
    ADD COLUMN IF NOT EXISTS "reconciled_by_username" varchar(100),
    ADD COLUMN IF NOT EXISTS "reconciled_at" timestamptz,
    ADD COLUMN IF NOT EXISTS "posted_at" timestamptz,
    ADD COLUMN IF NOT EXISTS "retry_count" integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "last_error" text,
    ADD COLUMN IF NOT EXISTS "last_attempt_at" timestamptz;

UPDATE "SepayTransaction"
SET "reconciliation_status" = 'PENDING_REVIEW'
WHERE "status" IN ('UNMATCHED', 'AMOUNT_MISMATCH', 'RECONCILIATION_REQUIRED')
  AND "reconciliation_status" = 'NONE';

CREATE INDEX IF NOT EXISTS "idx_sepay_tx_reconciliation_status"
    ON "SepayTransaction" ("reconciliation_status", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_sepay_tx_status_created"
    ON "SepayTransaction" ("status", "created_at" DESC);

CREATE TABLE IF NOT EXISTS "SepayReconciliationAudit" (
    "id" uuid PRIMARY KEY NOT NULL,
    "sepay_transaction_id" uuid NOT NULL,
    "action" varchar(50) NOT NULL,
    "from_status" varchar(50),
    "to_status" varchar(50),
    "actor_user_id" uuid,
    "actor_username" varchar(100),
    "reason" text,
    "evidence_reference" varchar(255),
    "related_booking_id" uuid,
    "related_payment_id" uuid,
    "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "fk_sepay_recon_audit_tx" FOREIGN KEY ("sepay_transaction_id")
        REFERENCES "SepayTransaction" ("id") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS "idx_sepay_recon_audit_tx"
    ON "SepayReconciliationAudit" ("sepay_transaction_id", "created_at" ASC);
