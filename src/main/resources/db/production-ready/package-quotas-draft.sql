-- Explicit proposed per-package quotas. These are catalogue configuration only;
-- booking/fulfillment services MUST enforce purchased snapshots before production sale.
BEGIN;
-- Scope narrowly to seed-owned DRAFT packages; never change external commercial packages.
UPDATE "Service" SET unit_code='SERVICE_UNIT', updated_at=now()
WHERE service_code LIKE 'VN-SVC-%' AND status::text='INACTIVE' AND unit_code IS NULL;
UPDATE "TourPackageService" ps
SET quantity_limit = CASE p.package_type::text
   WHEN 'BASIC' THEN 1 WHEN 'PLUS' THEN 2 WHEN 'ADVANCED' THEN 3
   WHEN 'PREMIUM' THEN 3 WHEN 'PREMIUM_PLUS' THEN 5 ELSE 1 END,
    unit_code='SERVICE_UNIT',owner_scope='PASSENGER'
FROM "TourPackage" p, "Service" s
WHERE ps.tour_package_id=p.tour_package_id AND ps.service_id=s.service_id
  AND p.package_code LIKE 'VN-PKG-%' AND p.status::text='DRAFT'
  AND s.service_code LIKE 'VN-SVC-%' AND ps.quantity_limit IS NULL;
UPDATE "TourPackageActivity" pa
SET quantity_limit = CASE WHEN p.package_type::text='PREMIUM_PLUS' THEN 2 ELSE 1 END,
    unit_code='PARTICIPATION',owner_scope='PASSENGER'
FROM "TourPackage" p,"OnboardActivity" a
WHERE pa.tour_package_id=p.tour_package_id AND pa.onboard_activity_id=a.onboard_activity_id
 AND p.package_code LIKE 'VN-PKG-%' AND p.status::text='DRAFT'
 AND a.activity_code LIKE 'VN-ACT-%' AND pa.quantity_limit IS NULL;
COMMIT;
