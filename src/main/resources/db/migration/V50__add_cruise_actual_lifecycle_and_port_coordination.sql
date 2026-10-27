-- V50__add_cruise_actual_lifecycle_and_port_coordination.sql
-- BE-20 & BE-21: Port coordination dossier, official clearance reference, and actual departure/return trace

-- 1. Extend CruiseStop with Port coordination and clearance metadata
ALTER TABLE "CruiseStop"
    ADD COLUMN IF NOT EXISTS "coordination_status" VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS "coordination_deadline" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS "dossier_reference" VARCHAR(100),
    ADD COLUMN IF NOT EXISTS "official_clearance_reference" VARCHAR(100),
    ADD COLUMN IF NOT EXISTS "clearance_authority" VARCHAR(150),
    ADD COLUMN IF NOT EXISTS "sequence_change_reason" VARCHAR(500);

COMMENT ON COLUMN "CruiseStop"."coordination_status" IS 'Trạng thái phối hợp cảng: NOT_STARTED, SUBMITTED, IN_COORDINATION, CONFIRMED, REJECTED';
COMMENT ON COLUMN "CruiseStop"."official_clearance_reference" IS 'Mã tham chiếu giấy phép rời/cập cảng từ cơ quan chức năng hải cảng bên ngoài (CASMS không tự cấp phép)';
COMMENT ON COLUMN "CruiseStop"."sequence_change_reason" IS 'Lý do thay đổi thứ tự hoặc điều chỉnh lượt ghé cảng';

CREATE INDEX IF NOT EXISTS idx_cruise_stop_coordination_status ON "CruiseStop"("coordination_status");

-- 2. Extend Cruise with actual lifecycle trace and completion audit
ALTER TABLE "Cruise"
    ADD COLUMN IF NOT EXISTS "actual_departure_datetime" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS "actual_return_datetime" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS "departure_actor_id" UUID REFERENCES "User"(user_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS "completion_actor_id" UUID REFERENCES "User"(user_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS "closure_notes" TEXT;

COMMENT ON COLUMN "Cruise"."actual_departure_datetime" IS 'Thời điểm khởi hành thực tế của chuyến đi (ghi nhận khi bấm Start sau khi vượt qua Departure Gate)';
COMMENT ON COLUMN "Cruise"."actual_return_datetime" IS 'Thời điểm kết thúc thực tế của chuyến đi (ghi nhận khi bấm Complete sau khi kiểm tra Checklist đóng chuyến)';

CREATE INDEX IF NOT EXISTS idx_cruise_actual_departure ON "Cruise"("actual_departure_datetime");
