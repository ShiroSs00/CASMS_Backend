-- V2: Add verification fields to OperationalIssue
-- Thêm cột verified_by và verified_at cho quy trình xác minh khắc phục sự cố

ALTER TABLE "OperationalIssue"
    ADD COLUMN IF NOT EXISTS "verified_by" uuid,
    ADD COLUMN IF NOT EXISTS "verified_at" timestamptz;

-- Thêm foreign key từ verified_by đến User.user_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_operational_issue_verified_by'
          AND conrelid = '"OperationalIssue"'::regclass
    ) THEN
        ALTER TABLE "OperationalIssue"
            ADD CONSTRAINT "fk_operational_issue_verified_by"
                FOREIGN KEY ("verified_by") REFERENCES "User" ("user_id");
    END IF;
END $$;

-- Tạo index cho verified_by để tối ưu truy vấn lọc theo người xác minh
CREATE INDEX IF NOT EXISTS "idx_operational_issue_verified_by" ON "OperationalIssue" ("verified_by");
