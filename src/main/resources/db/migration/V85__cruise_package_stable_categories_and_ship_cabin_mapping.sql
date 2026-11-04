-- SOURCE ONLY: additive; do not apply, backfill or rewrite historical Fare storage.
-- Expand the existing permission allowlist; no role assignments, seeds or runtime database writes here.
DO $$ DECLARE previous_expression text; BEGIN
 SELECT pg_get_expr(conbin,conrelid) INTO previous_expression FROM pg_constraint
 WHERE conrelid='"RolePermissionMapping"'::regclass AND conname='RolePermissionMapping_permission_check';
 IF previous_expression IS NOT NULL THEN
  ALTER TABLE "RolePermissionMapping" DROP CONSTRAINT "RolePermissionMapping_permission_check";
  EXECUTE format('ALTER TABLE "RolePermissionMapping" ADD CONSTRAINT "RolePermissionMapping_permission_check" CHECK ((%s) OR permission::text = %L)',previous_expression,'SHIP_CABIN_CATEGORY_MAPPING_CONFIRM');
 END IF;
END $$;
CREATE TABLE "CruisePackageLogicalCategory" (
 logical_category_id uuid PRIMARY KEY,
 origin_template_id uuid NOT NULL REFERENCES "FarePackageTemplate"(template_id),
 display_name varchar(100) NOT NULL
);
CREATE TABLE "CruisePackageVersionCategory" (
 logical_category_id uuid NOT NULL REFERENCES "CruisePackageLogicalCategory"(logical_category_id),
 template_version_id uuid NOT NULL REFERENCES "FarePackageTemplateVersion"(template_version_id),
 PRIMARY KEY(logical_category_id,template_version_id)
);
ALTER TABLE "DepartureFarePackage" ADD COLUMN logical_category_id uuid;
ALTER TABLE "DepartureFarePackage" ADD CONSTRAINT departure_cruise_package_category_version
 FOREIGN KEY(logical_category_id,template_version_id) REFERENCES "CruisePackageVersionCategory"(logical_category_id,template_version_id);
CREATE TABLE "ShipCruisePackageCabinMapping" (
 mapping_id uuid PRIMARY KEY,
 ship_id uuid NOT NULL REFERENCES "Ship"(ship_id),
 mapping_revision bigint NOT NULL CHECK(mapping_revision>0),
 structure_fingerprint varchar(64) NOT NULL,
 confirmed_by uuid NOT NULL REFERENCES "User"(user_id),
 confirmed_at timestamptz NOT NULL,
 UNIQUE(ship_id,mapping_revision)
);
CREATE TABLE "ShipCruisePackageCabinMappingItem" (
 mapping_id uuid NOT NULL REFERENCES "ShipCruisePackageCabinMapping"(mapping_id),
 logical_category_id uuid NOT NULL REFERENCES "CruisePackageLogicalCategory"(logical_category_id),
 cabin_id uuid REFERENCES "Cabin"(cabin_id) ON DELETE SET NULL,
 cabin_id_snapshot uuid NOT NULL,
 PRIMARY KEY(mapping_id,logical_category_id,cabin_id_snapshot), UNIQUE(mapping_id,cabin_id)
);
CREATE INDEX cruise_package_mapping_category ON "ShipCruisePackageCabinMappingItem"(logical_category_id,mapping_id);
ALTER TABLE "BookingFareSnapshot"
 ADD COLUMN logical_category_id uuid,
 ADD COLUMN cabin_mapping_id uuid REFERENCES "ShipCruisePackageCabinMapping"(mapping_id),
 ADD COLUMN physical_cabin_ids jsonb;

ALTER TABLE "BookingFareSnapshot" ADD CONSTRAINT booking_cruise_package_category_version
 FOREIGN KEY(logical_category_id,template_version_id) REFERENCES "CruisePackageVersionCategory"(logical_category_id,template_version_id);
