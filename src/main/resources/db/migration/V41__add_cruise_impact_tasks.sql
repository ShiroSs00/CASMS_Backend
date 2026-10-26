-- V41: Add CruiseImpactTask table for change impact assessment and recovery tasks (C17 / BE-10)

CREATE TABLE IF NOT EXISTS "CruiseImpactTask" (
    task_id UUID PRIMARY KEY,
    cruise_id UUID NOT NULL,
    task_code VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(250) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    assigned_to_user_id UUID,
    evidence TEXT,
    resolution_notes TEXT,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cruise_impact_task_cruise FOREIGN KEY (cruise_id) REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
    CONSTRAINT fk_cruise_impact_task_user FOREIGN KEY (assigned_to_user_id) REFERENCES "User"(user_id),
    CONSTRAINT uq_cruise_impact_task_code UNIQUE (task_code)
);

CREATE INDEX IF NOT EXISTS idx_cruise_impact_task_cruise ON "CruiseImpactTask"(cruise_id);
CREATE INDEX IF NOT EXISTS idx_cruise_impact_task_status ON "CruiseImpactTask"(status);
