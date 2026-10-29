-- V40__repair_edited_migrations_and_harden_cruise_source.sql
--
-- BE-FIX-00. V16 and V35 were edited after databases had already applied them.
-- Those two files are now restored byte-for-byte to the content that was first
-- applied. This forward-only migration carries the intended follow-up changes
-- without rewriting history, so every database converges to the same state:
--   * fresh databases (V1..V40 in one run)
--   * databases that applied the original V16/V35
--   * databases that recorded the edited V16/V35 checksums
--
-- Every statement is idempotent and keeps existing business data.

-- -----------------------------------------------------------------------------
-- 1. V35 follow-up (from commit 9d894af): the activity catalog was reduced to
--    four categories, so WORKSHOPS_CLASSES is no longer a valid
--    OnboardActivityCategory value. Rows that still carry it make JPA fail to
--    load the entity. Map them to ENTERTAINMENT, the replacement the edited
--    V35 chose for the same seed activities.
-- -----------------------------------------------------------------------------
UPDATE "OnboardActivity"
SET category = 'ENTERTAINMENT', updated_at = CURRENT_TIMESTAMP
WHERE category = 'WORKSHOPS_CLASSES';

UPDATE "TripPlanEntry"
SET category = 'ENTERTAINMENT'
WHERE category = 'WORKSHOPS_CLASSES';

-- -----------------------------------------------------------------------------
-- 2. V16 follow-up (from commit 45afd10): there is nothing to carry forward.
--    The edited V16 tried to insert a second account named 'operations_manager'
--    (a0000000-...-0017). V3 already seeds that username (a0000000-...-0005)
--    with the OPERATIONS_MANAGER role, and "User".username is UNIQUE. On a
--    fresh database the edited V16 therefore failed with a unique violation.
--    The OPERATIONS_MANAGER role and its RolePermission row come from V3/V17.
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 3. V37 hardening: Cruise source lineage (D01, C03, C15).
--    a) source_trip_id and source_version_id are set together or not at all.
--    b) source_version_id must belong to source_trip_id.
--    Cruises created before V37 have both columns NULL and stay valid.
--    Constraints are added NOT VALID first, so new writes are enforced
--    immediately. Existing rows are validated only when they already comply.
--    Otherwise the migration logs a WARNING instead of deleting or rewriting
--    data, and the offending rows can be repaired before a later VALIDATE.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = '"TripScheduleVersion"'::regclass
          AND conname = 'uq_trip_schedule_version_trip_version'
    ) THEN
        ALTER TABLE "TripScheduleVersion"
            ADD CONSTRAINT uq_trip_schedule_version_trip_version UNIQUE (trip_id, version_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = '"Cruise"'::regclass
          AND conname = 'ck_cruise_source_pair'
    ) THEN
        ALTER TABLE "Cruise"
            ADD CONSTRAINT ck_cruise_source_pair
            CHECK ((source_trip_id IS NULL) = (source_version_id IS NULL)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = '"Cruise"'::regclass
          AND conname = 'fk_cruise_source_trip_version'
    ) THEN
        ALTER TABLE "Cruise"
            ADD CONSTRAINT fk_cruise_source_trip_version
            FOREIGN KEY (source_trip_id, source_version_id)
            REFERENCES "TripScheduleVersion" (trip_id, version_id) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM "Cruise"
        WHERE (source_trip_id IS NULL) <> (source_version_id IS NULL)
    ) THEN
        RAISE WARNING 'V40: ck_cruise_source_pair left NOT VALID; some Cruise rows have only one of source_trip_id/source_version_id.';
    ELSE
        ALTER TABLE "Cruise" VALIDATE CONSTRAINT ck_cruise_source_pair;
    END IF;

    IF EXISTS (
        SELECT 1 FROM "Cruise" c
        WHERE c.source_trip_id IS NOT NULL
          AND c.source_version_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM "TripScheduleVersion" v
              WHERE v.version_id = c.source_version_id
                AND v.trip_id = c.source_trip_id
          )
    ) THEN
        RAISE WARNING 'V40: fk_cruise_source_trip_version left NOT VALID; some Cruise rows reference a version of a different Trip.';
    ELSE
        ALTER TABLE "Cruise" VALIDATE CONSTRAINT fk_cruise_source_trip_version;
    END IF;
END $$;

-- C15: list cruises created from a given Trip/version with stable ordering.
CREATE INDEX IF NOT EXISTS idx_cruise_source_version_departure
    ON "Cruise" (source_version_id, departure_datetime, cruise_id);
