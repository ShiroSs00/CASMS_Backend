ALTER TABLE "CruiseStop"
    ADD COLUMN disembarkation_starts_at TIMESTAMPTZ,
    ADD COLUMN disembarkation_minutes INTEGER,
    ADD COLUMN disembarkation_planned_arrival TIMESTAMPTZ,
    ADD COLUMN disembarkation_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT cruise_stop_disembarkation_complete CHECK (
        (disembarkation_starts_at IS NULL AND disembarkation_minutes IS NULL AND disembarkation_planned_arrival IS NULL)
        OR (disembarkation_starts_at IS NOT NULL AND disembarkation_minutes IS NOT NULL
            AND disembarkation_minutes > 0 AND disembarkation_planned_arrival IS NOT NULL
            AND disembarkation_starts_at >= disembarkation_planned_arrival));
