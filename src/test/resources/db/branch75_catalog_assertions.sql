-- Execute after opt-in seed and quota seed. Fails if any expected count or quota is missing.
DO $$
BEGIN
 IF (SELECT count(*) FROM "ServiceCategory" WHERE category_code LIKE 'VN-CAT-%') <> 12
   THEN RAISE EXCEPTION 'Expected 12 service categories'; END IF;
 IF (SELECT count(*) FROM "CatalogTranslation" WHERE entity_code LIKE 'VN-%' AND locale='vi') <> 181
   THEN RAISE EXCEPTION 'Expected 181 Vietnamese translations'; END IF;
 IF (SELECT count(DISTINCT package_type) FROM "TourPackage" WHERE package_code LIKE 'VN-PKG-%') <> 6
   THEN RAISE EXCEPTION 'Expected six distinct draft package types'; END IF;
 IF (SELECT count(*) FROM "TourPackageService" ps JOIN "TourPackage" p USING (tour_package_id)
   WHERE p.package_code LIKE 'VN-PKG-%') <> 112
   THEN RAISE EXCEPTION 'Expected 112 service benefit links'; END IF;
 IF (SELECT count(*) FROM "TourPackageActivity" pa JOIN "TourPackage" p USING (tour_package_id)
   WHERE p.package_code LIKE 'VN-PKG-%') <> 76
   THEN RAISE EXCEPTION 'Expected 76 activity benefit links'; END IF;
 IF EXISTS(SELECT 1 FROM "User" u JOIN "OperationalSeedEmployee" e
   ON lower(u.email)=lower(e.proposed_email) OR lower(u.username)=lower(e.proposed_email))
   THEN RAISE EXCEPTION 'Synthetic candidate has a provisioned account'; END IF;
 IF (SELECT count(*) FROM "OperationalSeedEmployee" WHERE employee_code LIKE 'VN-EMP-%') <> 120
   THEN RAISE EXCEPTION 'Expected 120 employee drafts'; END IF;
 IF (SELECT count(*) FROM "OperationalSeedTour" WHERE tour_code LIKE 'VN-SHORE-%') <> 20
   THEN RAISE EXCEPTION 'Expected 20 draft shore tours'; END IF;
 IF (SELECT count(*) FROM "Service" WHERE service_code LIKE 'VN-SVC-%' AND status::text='INACTIVE') <> 60
   THEN RAISE EXCEPTION 'Expected 60 inactive services'; END IF;
 IF (SELECT count(*) FROM "OnboardActivity" WHERE activity_code LIKE 'VN-ACT-%' AND catalog_entry) <> 35
   THEN RAISE EXCEPTION 'Expected 35 activity definitions'; END IF;
 IF (SELECT count(*) FROM "TourPackage" WHERE package_code LIKE 'VN-PKG-%' AND status::text='DRAFT') <> 24
   THEN RAISE EXCEPTION 'Expected 24 draft packages'; END IF;
 IF (SELECT count(*) FROM "CommercialPolicyCatalog" WHERE policy_code LIKE 'VN-POL-%' AND status='DRAFT') <> 30
   THEN RAISE EXCEPTION 'Expected 30 draft policies'; END IF;
 IF EXISTS(SELECT 1 FROM "TourPackageService" ps JOIN "TourPackage" p USING (tour_package_id)
   WHERE p.package_code LIKE 'VN-PKG-%' AND (ps.quantity_limit IS NULL OR ps.quantity_limit < 1 OR ps.unit_code IS NULL))
   THEN RAISE EXCEPTION 'Draft package service quotas incomplete'; END IF;
 IF EXISTS(SELECT 1 FROM "TourPackageActivity" pa JOIN "TourPackage" p USING (tour_package_id)
   WHERE p.package_code LIKE 'VN-PKG-%' AND (pa.quantity_limit IS NULL OR pa.quantity_limit < 1 OR pa.unit_code IS NULL))
   THEN RAISE EXCEPTION 'Draft package activity quotas incomplete'; END IF;
 IF EXISTS(SELECT 1 FROM "OperationalSeedTour" WHERE tour_code LIKE 'VN-SHORE-%' AND status='VERIFIED')
   THEN RAISE NOTICE 'Verified tours exist; check human audit evidence individually'; END IF;
END $$;
