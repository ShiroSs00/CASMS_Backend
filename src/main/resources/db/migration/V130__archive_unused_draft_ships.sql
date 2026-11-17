ALTER TABLE "Ship"
    ADD COLUMN archived_at timestamptz,
    ADD COLUMN archived_by uuid REFERENCES "User"(user_id),
    ADD CONSTRAINT ck_ship_archive CHECK (
        (archived_at IS NULL AND archived_by IS NULL)
        OR (archived_at IS NOT NULL AND archived_by IS NOT NULL AND status = 'DRAFT'));

CREATE TABLE "ShipCruiseUsage" (
    ship_id uuid PRIMARY KEY REFERENCES "Ship"(ship_id)
);

INSERT INTO "ShipCruiseUsage" (ship_id)
SELECT DISTINCT ship_id FROM "Cruise" WHERE ship_id IS NOT NULL;

INSERT INTO "ShipCruiseUsage" (ship_id)
SELECT DISTINCT s.ship_id
FROM "CruiseAuditEvent" e
CROSS JOIN LATERAL (VALUES (e.before_digest), (e.after_digest)) AS change(digest)
JOIN "Ship" s ON s.ship_id::text = lower(CASE
    WHEN e.event_type = 'CRUISE_APPLY_CHANGE' THEN substring(change.digest FROM ', Ship=([0-9a-fA-F-]{36})$')
    ELSE substring(change.digest FROM '\(([0-9a-fA-F-]{36})\)$')
END)
WHERE e.event_type IN ('SHIP_ASSIGNMENT', 'SHIP_CHANGE', 'SHIP_UNASSIGNMENT', 'CRUISE_APPLY_CHANGE')
ON CONFLICT DO NOTHING;

-- The catalog names the ownership key for each reference audited at V129.
-- NULL blocker marks generated structure. Writes still take the ship lock.
CREATE VIEW casms_ship_archive_dependencies AS
SELECT relation::regclass AS relation, field, target, blocker
FROM (VALUES
    ('"Cruise"', 'ship_id', 'Ship', 'CRUISE_HISTORY'),
    ('"Deck"', 'ship_id', 'Ship', NULL),
    ('"Cabin"', 'ship_id', 'Ship', NULL),
    ('"Cabin"', 'deck_id', 'Deck', NULL),
    ('"ShipModel"', 'ship_id', 'Ship', NULL),
    ('"ShipModelObject"', 'ship_model_id', 'ShipModel', NULL),
    ('"ShipModelObject"', 'deck_id', 'Deck', NULL),
    ('"ShipModelObject"', 'cabin_id', 'Cabin', NULL),
    ('"ModelImportJob"', 'ship_model_id', 'ShipModel', 'MODEL_IMPORT_RUNNING'),
    ('"OperationalIssue"', 'ship_id', 'Ship', 'OPERATIONAL_HISTORY'),
    ('"OperationalIssue"', 'cabin_id', 'Cabin', 'OPERATIONAL_HISTORY'),
    ('"ShipRegulation"', 'ship_id', 'Ship', 'OPERATIONAL_HISTORY'),
    ('"TripPlanEntry"', 'deck_id', 'Deck', 'TRIP_PLANNING'),
    ('"TripPlanEntry"', 'cabin_id', 'Cabin', 'TRIP_PLANNING'),
    ('"OnboardActivity"', 'deck_id', 'Deck', 'ACTIVITY_CONFIGURATION'),
    ('"OnboardActivity"', 'cabin_id', 'Cabin', 'ACTIVITY_CONFIGURATION'),
    ('"OnboardActivity"', 'area_object_id', 'ShipModelObject', 'ACTIVITY_CONFIGURATION'),
    ('"LocationDevice"', 'ship_id', 'Ship', 'BLE_HISTORY'),
    ('"LocationDevice"', 'deck_id', 'Deck', 'BLE_HISTORY'),
    ('"LocationRecord"', 'ship_id', 'Ship', 'BLE_HISTORY'),
    ('"DeckServiceAssignment"', 'deck_id', 'Deck', 'SERVICE_ASSIGNMENT'),
    ('"AreaServiceAssignment"', 'area_object_id', 'ShipModelObject', 'SERVICE_ASSIGNMENT'),
    ('"AreaActivityAssignment"', 'area_object_id', 'ShipModelObject', 'ACTIVITY_CONFIGURATION'),
    ('"AreaStaffAssignment"', 'area_object_id', 'ShipModelObject', 'STAFFING'),
    ('"PosTerminal"', 'ship_id', 'Ship', 'POS_HISTORY'),
    ('"PosTerminal"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"PosTerminalArea"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"PosSale"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"PosSession"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"PosAreaActivityPermission"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"PosAreaServicePermission"', 'area_object_id', 'ShipModelObject', 'POS_HISTORY'),
    ('"CabinAssignment"', 'cabin_id', 'Cabin', 'PASSENGER_ASSIGNMENT'),
    ('"BookingFareSnapshot"', 'ship_id', 'Ship', 'BOOKING'),
    ('"DepartureFarePackage"', 'ship_id', 'Ship', 'CRUISE_PACKAGE'),
    ('"ShipCruisePackageCabinMapping"', 'ship_id', 'Ship', 'CRUISE_PACKAGE'),
    ('"ShipCruisePackageCabinMappingItem"', 'cabin_id', 'Cabin', 'CRUISE_PACKAGE'),
    ('"ShipCruisePackageCabinMappingItem"', 'cabin_id_snapshot', 'Cabin', 'CRUISE_PACKAGE'),
    ('"CruisePackageAddonOffer"', 'ship_id', 'Ship', 'CRUISE_PACKAGE'),
    ('"FarePackageTemplateVersion"', 'source_ship_id', 'Ship', 'CRUISE_PACKAGE'),
    ('"CruisePackageLogicalCategory"', 'source_ship_id', 'Ship', 'CRUISE_PACKAGE'),
    ('"CruiseServiceLocationRequest"', 'deck_id', 'Deck', 'SERVICE_ASSIGNMENT')
) AS refs(relation, field, target, blocker);

CREATE VIEW casms_ship_archive_objects AS
SELECT 'Ship'::text AS kind, ship_id AS id, ship_id FROM "Ship"
UNION ALL SELECT 'Deck', deck_id, ship_id FROM "Deck"
UNION ALL SELECT 'Cabin', cabin_id, ship_id FROM "Cabin"
UNION ALL SELECT 'ShipModel', ship_model_id, ship_id FROM "ShipModel"
UNION ALL SELECT 'ShipModelObject', o.ship_model_object_id, m.ship_id
  FROM "ShipModelObject" o JOIN "ShipModel" m USING (ship_model_id);

CREATE FUNCTION casms_ship_archive_blockers(vessel uuid)
RETURNS TABLE (blocker text) LANGUAGE plpgsql STABLE AS $$
DECLARE
    ref record;
    blocker_codes text[] := ARRAY[]::text[];
    present boolean;
BEGIN
    IF EXISTS (SELECT 1 FROM "ShipCruiseUsage" WHERE ship_id = vessel) THEN
        blocker_codes := array_append(blocker_codes, 'CRUISE_HISTORY');
    END IF;
    FOR ref IN SELECT * FROM casms_ship_archive_dependencies d WHERE d.blocker IS NOT NULL LOOP
        IF ref.blocker = ANY(blocker_codes) THEN CONTINUE; END IF;
        EXECUTE format('SELECT EXISTS (
            SELECT 1 FROM %s r JOIN casms_ship_archive_objects o ON o.id = r.%I
            WHERE o.ship_id = $1 AND o.kind = $2 %s)', ref.relation, ref.field,
            CASE WHEN ref.blocker = 'MODEL_IMPORT_RUNNING' THEN 'AND r.status = ''RUNNING''' ELSE '' END)
          INTO present USING vessel, ref.target;
        IF present THEN blocker_codes := array_append(blocker_codes, ref.blocker); END IF;
    END LOOP;
    RETURN QUERY SELECT unnest(blocker_codes);
END;
$$;

-- FOR SHARE conflicts with archive's FOR UPDATE. Under READ COMMITTED a waiter
-- rechecks the locked row after the competing transaction commits.
CREATE FUNCTION casms_ship_archive_lock(vessel uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    archived timestamptz;
BEGIN
    SELECT archived_at INTO archived FROM "Ship" WHERE ship_id = vessel FOR SHARE;
    IF archived IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'casms_ship_archived';
    END IF;
END;
$$;

CREATE FUNCTION casms_ship_archive_guard_reference() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    ids uuid[] := ARRAY[]::uuid[];
    vessel uuid;
BEGIN
    IF TG_OP <> 'INSERT' THEN ids := array_append(ids, (to_jsonb(OLD)->>TG_ARGV[0])::uuid); END IF;
    IF TG_OP <> 'DELETE' THEN ids := array_append(ids, (to_jsonb(NEW)->>TG_ARGV[0])::uuid); END IF;
    FOR vessel IN SELECT DISTINCT ship_id FROM casms_ship_archive_objects
        WHERE kind = TG_ARGV[1] AND id = ANY(ids) ORDER BY ship_id LOOP
        PERFORM casms_ship_archive_lock(vessel);
        IF TG_TABLE_NAME = 'Cruise' THEN
            INSERT INTO "ShipCruiseUsage" (ship_id) VALUES (vessel) ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    ref record;
BEGIN
    FOR ref IN SELECT * FROM casms_ship_archive_dependencies LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE INSERT OR UPDATE OR DELETE ON %s
            FOR EACH ROW EXECUTE FUNCTION casms_ship_archive_guard_reference(%L, %L)',
            'casms_archive_' || ref.field, ref.relation, ref.field, ref.target);
    END LOOP;
END;
$$;

CREATE FUNCTION casms_ship_archive_guard_vessel() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.archived_at IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'casms_ship_archived';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER casms_archive_vessel BEFORE UPDATE OR DELETE ON "Ship"
    FOR EACH ROW EXECUTE FUNCTION casms_ship_archive_guard_vessel();
