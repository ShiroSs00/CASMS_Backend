-- V42: Enhance CruiseImpactTask and Cruise for C17 Task deduplication, event tracking, and stop-sale auditing (BE-R05b, BE-R06, BE-R07)

ALTER TABLE "CruiseImpactTask"
    ADD COLUMN IF NOT EXISTS event_identity VARCHAR(100),
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS resolved_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_cruise_impact_task_resolved_by'
    ) THEN
        ALTER TABLE "CruiseImpactTask"
            ADD CONSTRAINT fk_cruise_impact_task_resolved_by
            FOREIGN KEY (resolved_by_user_id) REFERENCES "User"(user_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_cruise_impact_task_event ON "CruiseImpactTask"(event_identity);
CREATE INDEX IF NOT EXISTS idx_cruise_impact_task_target ON "CruiseImpactTask"(target_type, target_id);

ALTER TABLE "Cruise"
    ADD COLUMN IF NOT EXISTS stopped_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS stopped_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS stop_reason TEXT,
    ADD COLUMN IF NOT EXISTS stop_evidence TEXT,
    ADD COLUMN IF NOT EXISTS stop_scope VARCHAR(50),
    ADD COLUMN IF NOT EXISTS stop_event_identity VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_cruise_stopped_by'
    ) THEN
        ALTER TABLE "Cruise"
            ADD CONSTRAINT fk_cruise_stopped_by
            FOREIGN KEY (stopped_by_user_id) REFERENCES "User"(user_id);
    END IF;
END $$;
