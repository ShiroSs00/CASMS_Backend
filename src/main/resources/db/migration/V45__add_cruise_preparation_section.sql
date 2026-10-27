-- BE-07: Add cruise preparation sections table
CREATE TABLE IF NOT EXISTS "CruisePreparationSection" (
    "preparation_id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "cruise_id" UUID NOT NULL,
    "section_code" VARCHAR(50) NOT NULL,
    "section_name" VARCHAR(100) NOT NULL,
    "owner_role" VARCHAR(50) NOT NULL,
    "status" VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    "evidence" TEXT,
    "notes" TEXT,
    "submitted_by_user_id" UUID,
    "submitted_at" TIMESTAMP WITH TIME ZONE,
    "confirmed_by_user_id" UUID,
    "confirmed_by_username" VARCHAR(100),
    "confirmed_at" TIMESTAMP WITH TIME ZONE,
    "returned_by_user_id" UUID,
    "return_reason" TEXT,
    "returned_at" TIMESTAMP WITH TIME ZONE,
    "revision" BIGINT NOT NULL DEFAULT 0,
    "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT "fk_preparation_cruise" FOREIGN KEY ("cruise_id") REFERENCES "Cruise"("cruise_id") ON DELETE CASCADE,
    CONSTRAINT "uk_cruise_section" UNIQUE ("cruise_id", "section_code")
);

CREATE INDEX IF NOT EXISTS "idx_preparation_cruise_id" ON "CruisePreparationSection"("cruise_id");
