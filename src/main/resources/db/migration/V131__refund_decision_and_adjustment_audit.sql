ALTER TABLE "Refund"
    ADD COLUMN rejection_reason text,
    ADD COLUMN rejected_by_user_id uuid REFERENCES "User"(user_id),
    ADD COLUMN rejected_at timestamptz,
    ADD COLUMN adjustment_invoice_id uuid UNIQUE REFERENCES "Invoice"(invoice_id),
    ADD CONSTRAINT refund_rejection_audit_complete CHECK (
        (rejection_reason IS NULL AND rejected_by_user_id IS NULL AND rejected_at IS NULL)
        OR (status = 'Rejected' AND rejection_reason IS NOT NULL
            AND btrim(rejection_reason) <> '' AND rejected_by_user_id IS NOT NULL AND rejected_at IS NOT NULL)
    );
