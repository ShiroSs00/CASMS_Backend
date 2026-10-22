-- V11: Add verification columns to Supplier table
-- Thêm các trường xác minh cho Supplier: verification_status, verified_at, verified_by, verification_notes

ALTER TABLE "Supplier"
    ADD COLUMN IF NOT EXISTS "verification_status" VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS "verified_at" TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS "verified_by" UUID,
    ADD COLUMN IF NOT EXISTS "verification_notes" VARCHAR(1000);

-- Ràng buộc trạng thái xác minh hợp lệ
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'Supplier_verification_status_check'
    ) THEN
        ALTER TABLE "Supplier"
            ADD CONSTRAINT "Supplier_verification_status_check"
            CHECK ("verification_status" IN ('PENDING', 'VERIFIED', 'REJECTED'));
    END IF;
END $$;

-- Khóa ngoại từ verified_by đến User.user_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_supplier_verified_by'
    ) THEN
        ALTER TABLE "Supplier"
            ADD CONSTRAINT "fk_supplier_verified_by"
            FOREIGN KEY ("verified_by") REFERENCES "User" ("user_id");
    END IF;
END $$;

-- Index cho verified_by
CREATE INDEX IF NOT EXISTS "idx_supplier_verified_by" ON "Supplier" ("verified_by");

COMMENT ON COLUMN "Supplier"."verification_status" IS 'Trạng thái xác minh: PENDING, VERIFIED, REJECTED.';
COMMENT ON COLUMN "Supplier"."verified_at" IS 'Thời điểm xác minh nhà cung cấp.';
COMMENT ON COLUMN "Supplier"."verified_by" IS 'ID nhân viên thực hiện xác minh.';
COMMENT ON COLUMN "Supplier"."verification_notes" IS 'Ghi chú xác minh nhà cung cấp.';
