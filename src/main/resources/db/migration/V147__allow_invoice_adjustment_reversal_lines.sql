-- Existing refund completion writes a signed reversal line on AdjustmentStatement.
-- Preserve ordinary invoice line invariants and allow only that existing reversal shape.
ALTER TABLE "InvoiceLine" DROP CONSTRAINT "InvoiceLine_base_amount_snapshot_check";
ALTER TABLE "InvoiceLine" DROP CONSTRAINT "InvoiceLine_total_amount_snapshot_check";
ALTER TABLE "InvoiceLine" DROP CONSTRAINT "InvoiceLine_check";

ALTER TABLE "InvoiceLine" ADD CONSTRAINT invoice_line_source_or_reversal_check CHECK (
    ((base_amount_snapshot >= 0 AND total_amount_snapshot >= 0)
        AND ((source_charge_id IS NOT NULL AND booking_fare_amount_snapshot IS NULL)
            OR (source_charge_id IS NULL AND booking_fare_amount_snapshot IS NOT NULL)))
    OR (base_amount_snapshot < 0 AND total_amount_snapshot = base_amount_snapshot
        AND tax_amount_snapshot = 0 AND source_charge_id IS NULL
        AND booking_fare_amount_snapshot IS NULL)
);

CREATE FUNCTION validate_invoice_reversal_document() RETURNS trigger AS $$
BEGIN
    IF NEW.base_amount_snapshot < 0 OR NEW.total_amount_snapshot < 0 THEN
        PERFORM 1 FROM "Invoice" WHERE invoice_id=NEW.invoice_id
            AND document_type='AdjustmentStatement' FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Signed reversal lines require an adjustment statement'
                USING ERRCODE='23514', CONSTRAINT='invoice_reversal_document_check';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_line_reversal_document_guard
    BEFORE INSERT OR UPDATE ON "InvoiceLine"
    FOR EACH ROW EXECUTE FUNCTION validate_invoice_reversal_document();

CREATE FUNCTION protect_invoice_reversal_document_type() RETURNS trigger AS $$
BEGIN
    IF NEW.document_type <> 'AdjustmentStatement' AND EXISTS (
        SELECT 1 FROM "InvoiceLine" WHERE invoice_id=NEW.invoice_id
            AND (base_amount_snapshot < 0 OR total_amount_snapshot < 0)
    ) THEN
        RAISE EXCEPTION 'An invoice with reversal lines must remain an adjustment statement'
            USING ERRCODE='23514', CONSTRAINT='invoice_reversal_document_check';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_reversal_document_type_guard
    BEFORE UPDATE OF document_type ON "Invoice"
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_reversal_document_type();
