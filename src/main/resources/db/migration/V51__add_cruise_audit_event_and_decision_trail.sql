-- V51__add_cruise_audit_event_and_decision_trail.sql
-- BE-25: Unified Cruise business events audit trail and OPS decision traceability

CREATE TABLE IF NOT EXISTS "CruiseAuditEvent" (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cruise_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_identity VARCHAR(100) NOT NULL,
    actor_id UUID,
    actor_username VARCHAR(100),
    actor_role VARCHAR(100),
    required_permission VARCHAR(100),
    scope VARCHAR(50) NOT NULL DEFAULT 'WHOLE_CRUISE',
    before_digest TEXT,
    after_digest TEXT,
    reason TEXT,
    evidence TEXT,
    fingerprint VARCHAR(100),
    source_version_id UUID,
    revision BIGINT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cruise_audit_event_cruise FOREIGN KEY (cruise_id) REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cruise_audit_event_cruise_time ON "CruiseAuditEvent"(cruise_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_cruise_audit_event_type ON "CruiseAuditEvent"(event_type);
CREATE INDEX IF NOT EXISTS idx_cruise_audit_event_identity ON "CruiseAuditEvent"(event_identity);
