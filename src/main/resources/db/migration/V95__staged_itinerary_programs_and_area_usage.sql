-- Explicit classification; never infer that an unknown legacy facility is an activity venue.
ALTER TABLE "ShipModelObject" ADD COLUMN area_usage varchar(24) NOT NULL DEFAULT 'OTHER';
ALTER TABLE "ShipModelObject" ADD CONSTRAINT ship_area_usage_check
    CHECK (area_usage IN ('OTHER','SERVICE','ACTIVITY_SPACE'));
UPDATE "ShipModelObject" SET area_usage='SERVICE'
 WHERE ship_model_object_id IN (SELECT area_object_id FROM "AreaServiceAssignment" WHERE enabled);
UPDATE "ShipModelObject" o SET area_usage='ACTIVITY_SPACE'
 WHERE EXISTS(SELECT 1 FROM "AreaActivityAssignment" a WHERE a.area_object_id=o.ship_model_object_id AND a.enabled)
 AND NOT EXISTS(SELECT 1 FROM "AreaServiceAssignment" s WHERE s.area_object_id=o.ship_model_object_id AND s.enabled);

ALTER TABLE "OnboardActivity" ADD COLUMN area_object_id uuid REFERENCES "ShipModelObject"(ship_model_object_id);
ALTER TABLE "ShoreExcursion" ADD COLUMN source_excursion_id uuid REFERENCES "ShoreExcursion"(shore_excursion_id);
ALTER TABLE "OnboardActivity" ALTER COLUMN capacity DROP NOT NULL;
ALTER TABLE "OnboardActivity" DROP CONSTRAINT activity_catalog_schedule_check;
ALTER TABLE "OnboardActivity" ADD CONSTRAINT activity_catalog_schedule_check CHECK (
    (catalog_entry AND start_time IS NULL AND end_time IS NULL AND cruise_id IS NULL
        AND deck_id IS NULL AND cabin_id IS NULL AND area_object_id IS NULL AND location IS NULL) OR
    (NOT catalog_entry AND (status IN ('PLANNED','CANCELLED') OR
        (start_time IS NOT NULL AND end_time IS NOT NULL))));
ALTER TABLE "OnboardActivity" DROP CONSTRAINT onboard_actual_times_required;
ALTER TABLE "OnboardActivity" ADD CONSTRAINT onboard_actual_times_required CHECK (
    cruise_id IS NULL OR status IN ('PLANNED','CANCELLED') OR
    (start_time IS NOT NULL AND end_time IS NOT NULL AND capacity IS NOT NULL));
ALTER TABLE "ShoreExcursion" DROP CONSTRAINT excursion_actual_fields_required;
ALTER TABLE "ShoreExcursion" ADD CONSTRAINT excursion_actual_fields_required CHECK (
    cruise_stop_id IS NULL OR status IN ('PLANNED','CANCELLED') OR
    (start_time IS NOT NULL AND planned_return_time IS NOT NULL AND maximum_capacity IS NOT NULL));
CREATE INDEX ix_activity_area ON "OnboardActivity" (area_object_id) WHERE area_object_id IS NOT NULL;

-- Area row locks serialize both types of assignment, including legacy POS endpoints.
CREATE FUNCTION guard_program_area_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE usage_value text;
BEGIN
    IF NOT NEW.enabled THEN RETURN NEW; END IF;
    SELECT area_usage INTO usage_value FROM "ShipModelObject"
      WHERE ship_model_object_id=NEW.area_object_id FOR UPDATE;
    IF TG_TABLE_NAME='AreaServiceAssignment' AND usage_value='ACTIVITY_SPACE' THEN
        RAISE EXCEPTION 'Activity spaces cannot host service assignments' USING ERRCODE='23514';
    ELSIF TG_TABLE_NAME='AreaActivityAssignment' AND (usage_value<>'ACTIVITY_SPACE' OR EXISTS(
        SELECT 1 FROM "AreaServiceAssignment" WHERE area_object_id=NEW.area_object_id AND enabled)) THEN
        RAISE EXCEPTION 'Activity assignment requires a dedicated activity space' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_area_service BEFORE INSERT OR UPDATE ON "AreaServiceAssignment"
 FOR EACH ROW EXECUTE FUNCTION guard_program_area_assignment();
CREATE TRIGGER guard_area_activity BEFORE INSERT OR UPDATE ON "AreaActivityAssignment"
 FOR EACH ROW EXECUTE FUNCTION guard_program_area_assignment();

CREATE FUNCTION guard_activity_space_reclassification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.area_usage<>OLD.area_usage AND (
        (NEW.area_usage='ACTIVITY_SPACE' AND EXISTS(SELECT 1 FROM "AreaServiceAssignment" WHERE area_object_id=NEW.ship_model_object_id AND enabled))
        OR (NEW.area_usage<>'ACTIVITY_SPACE' AND (EXISTS(SELECT 1 FROM "OnboardActivity" WHERE area_object_id=NEW.ship_model_object_id AND status IN ('PLANNED','OPEN','FULL','IN_PROGRESS'))
            OR EXISTS(SELECT 1 FROM "AreaActivityAssignment" WHERE area_object_id=NEW.ship_model_object_id AND enabled)))) THEN
        RAISE EXCEPTION 'Area classification conflicts with existing enabled assignments' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_activity_space_usage BEFORE UPDATE OF area_usage ON "ShipModelObject"
 FOR EACH ROW EXECUTE FUNCTION guard_activity_space_reclassification();
