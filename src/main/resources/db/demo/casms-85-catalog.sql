-- Local-only CASMS demo catalogue. Safe for dev/demo profile startup; never configure in production.
-- Safe to rerun: only rows with the reserved DEMO-* business codes are inserted/updated.
BEGIN;

-- Isolated demonstration inventory; no cruise/booking is fabricated.
INSERT INTO "Ship" (ship_id,ship_code,ship_name,status,total_capacity,total_decks,commission_date,created_at,updated_at)
SELECT CASE WHEN EXISTS (SELECT 1 FROM "Ship" WHERE ship_id='85000000-0000-4000-8000-000000000201')
       THEN gen_random_uuid() ELSE '85000000-0000-4000-8000-000000000201'::uuid END,
       'DEMO-CATALOG-SHIP','Demo catalogue ship','ACTIVE',8,1,CURRENT_DATE,now(),now()
ON CONFLICT (ship_code) DO NOTHING;
INSERT INTO "Deck" (deck_id,ship_id,deck_number,deck_name,created_at,updated_at)
SELECT CASE WHEN EXISTS (SELECT 1 FROM "Deck" WHERE deck_id='85000000-0000-4000-8000-000000000202')
       THEN gen_random_uuid() ELSE '85000000-0000-4000-8000-000000000202'::uuid END,
       s.ship_id,1,'Demo deck',now(),now()
FROM "Ship" s WHERE s.ship_code='DEMO-CATALOG-SHIP'
ON CONFLICT (ship_id,deck_number) DO NOTHING;
-- Preserve any legacy V34 inventory that already owns the originally reserved UUIDs.
WITH seed(cabin_id,cabin_number,cabin_type,capacity,status,is_accessible,created_at,updated_at,minimum_occupancy,bed_count,bed_configuration,area_square_meters,balcony_area_square_meters,location_code,has_balcony,has_connecting_door,is_family_cabin,has_bathtub,has_separate_living_area) AS (VALUES
 ('85000000-0000-4000-8000-000000000203','101','INTERIOR',2,'AVAILABLE',false,now(),now(),1,2,'TWIN',14,NULL,'AFT-PORT',false,false,false,false,false),
 ('85000000-0000-4000-8000-000000000204','102','OCEAN_VIEW',2,'AVAILABLE',false,now(),now(),1,2,'TWIN',17,NULL,'MIDSHIP-PORT',false,false,false,false,false),
 ('85000000-0000-4000-8000-000000000205','103','BALCONY',2,'AVAILABLE',false,now(),now(),1,2,'QUEEN',21,4,'MIDSHIP-STARBOARD',true,false,false,true,false),
 ('85000000-0000-4000-8000-000000000206','104','SUITE',2,'AVAILABLE',false,now(),now(),1,1,'KING',32,8,'FORWARD-STARBOARD',true,false,false,true,true))
INSERT INTO "Cabin" (cabin_id,ship_id,deck_id,cabin_number,cabin_type,capacity,status,is_accessible,created_at,updated_at,
                      minimum_occupancy,bed_count,bed_configuration,area_square_meters,balcony_area_square_meters,
                      location_code,has_balcony,has_connecting_door,is_family_cabin,has_bathtub,has_separate_living_area)
SELECT CASE WHEN EXISTS (SELECT 1 FROM "Cabin" old WHERE old.cabin_id=seed.cabin_id::uuid)
       THEN gen_random_uuid() ELSE seed.cabin_id::uuid END, s.ship_id,d.deck_id,
       seed.cabin_number,seed.cabin_type::"Cabin_cabin_type",seed.capacity,seed.status::"Cabin_status",seed.is_accessible,seed.created_at,seed.updated_at,seed.minimum_occupancy,seed.bed_count,seed.bed_configuration,seed.area_square_meters,seed.balcony_area_square_meters,seed.location_code,seed.has_balcony,seed.has_connecting_door,seed.is_family_cabin,seed.has_bathtub,seed.has_separate_living_area
FROM seed CROSS JOIN "Ship" s JOIN "Deck" d ON d.ship_id=s.ship_id AND d.deck_number=1
WHERE s.ship_code='DEMO-CATALOG-SHIP'
ON CONFLICT (ship_id,cabin_number) DO NOTHING;

INSERT INTO "ServiceCategory" (category_code, category_name)
VALUES
 ('DINING','Dining'), ('BEVERAGE','Beverage'), ('INTERNET','Internet and connectivity'),
 ('SHORE_EXCURSION','Shore excursions'), ('SPA_WELLNESS','Spa and wellness'),
 ('ENTERTAINMENT_ACTIVITY','Entertainment and activities'), ('PHOTOGRAPHY','Photography'),
 ('CABIN_SERVICE','Cabin services'), ('SHOPPING_RETAIL','Shopping and retail'),
 ('LAUNDRY','Laundry'), ('CHILDCARE_FAMILY','Childcare and family'),
 ('MEDICAL_ASSISTANCE','Medical assistance'), ('TRANSPORTATION','Transportation')
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO "Service" (service_id, service_code, service_name, description, service_type,
                        price, currency_code, capacity, status, created_at, updated_at)
VALUES
 ('85000000-0000-4000-8000-000000000001', 'DEMO-WIFI', 'Demo onboard Wi-Fi',
  'Local demo catalogue item; not an OceVie commercial price.', 'INTERNET',
  0.0000, 'USD', NULL, 'ACTIVE', now(), now()),
 ('85000000-0000-4000-8000-000000000002', 'DEMO-SPA', 'Demo spa appointment',
  'Local demo catalogue item; capacity applies across active requests because service sessions are not modeled.', 'WELLNESS',
  25.0000, 'USD', 8, 'ACTIVE', now(), now()),
 ('85000000-0000-4000-8000-000000000003', 'DEMO-DINING', 'Demo dining reservation',
  'Local demo catalogue item; not an OceVie commercial price.', 'DINING',
  0.0000, 'USD', 24, 'ACTIVE', now(), now())
ON CONFLICT (service_code) DO NOTHING;

-- Add a compact, representative sample across all service categories. All prices are illustrative USD.
INSERT INTO "Service" (service_id, service_code, service_name, description, service_type,
                        price, currency_code, capacity, status, created_at, updated_at)
VALUES
 ('85000000-0000-4000-8000-000000000101','DEMO-DINING-MAIN','Main dining reservation','Demo standard dining reservation.','DINING',0,'USD',120,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000102','DEMO-DINING-SPECIALTY','Specialty restaurant dining','Demo specialty dining cover.','DINING',45,'USD',24,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000103','DEMO-BEV-NONALCOHOLIC','Non-alcoholic beverage package','Illustrative per-passenger beverage allowance.','BEVERAGE',18,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000104','DEMO-BEV-PREMIUM','Premium beverage package','Illustrative package; age rules require operational policy.','BEVERAGE',65,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000105','DEMO-INTERNET-WEB','Web and messaging access','Illustrative per-device connectivity access.','INTERNET',12,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000106','DEMO-INTERNET-STREAM','Streaming connectivity upgrade','Illustrative streaming tier; no fixed throughput promise.','INTERNET',22,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000107','DEMO-SHORE-CITY','City highlights excursion','Catalog placeholder; requires a valid cruise stop before scheduling.','SHORE_EXCURSION',55,'USD',30,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000108','DEMO-SHORE-SNORKEL','Snorkeling excursion','Catalog placeholder; schedule and supplier assigned per cruise.','SHORE_EXCURSION',75,'USD',12,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000109','DEMO-SPA-MASSAGE','Relaxation massage','Illustrative appointment service; staff and schedule required.','SPA_WELLNESS',90,'USD',1,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000110','DEMO-SPA-YOGA','Yoga class','Illustrative scheduled wellness class.','SPA_WELLNESS',15,'USD',20,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000111','DEMO-ACTIVITY-SHOW','Theatre performance','Included entertainment example; reservation may be capacity limited.','ENTERTAINMENT_ACTIVITY',0,'USD',150,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000112','DEMO-ACTIVITY-COOKING','Cooking class','Illustrative scheduled class.','ENTERTAINMENT_ACTIVITY',30,'USD',16,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000113','DEMO-PHOTO-DIGITAL','Digital photo collection','Illustrative per collection photography service.','PHOTOGRAPHY',35,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000114','DEMO-PHOTO-SESSION','Private photo session','Illustrative scheduled private session.','PHOTOGRAPHY',120,'USD',1,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000115','DEMO-CABIN-ROOM-SERVICE','Cabin room service','Illustrative per-order cabin service.','CABIN_SERVICE',12,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000116','DEMO-CABIN-CELEBRATION','Celebration cabin setup','Illustrative cabin amenity.','CABIN_SERVICE',40,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000118','DEMO-LAUNDRY-BAG','Laundry bag service','Illustrative per-bag laundry service.','LAUNDRY',25,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000119','DEMO-FAMILY-CHILDCARE','Supervised childcare session','Illustrative; do not treat as a real safety policy.','CHILDCARE_FAMILY',20,'USD',8,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000120','DEMO-MEDICAL-FIRST-AID','Onboard first-aid assistance','Catalog example only; not an emergency service substitute.','MEDICAL_ASSISTANCE',0,'USD',NULL,'ACTIVE',now(),now()),
 ('85000000-0000-4000-8000-000000000121','DEMO-TRANSPORT-PORT','Port transfer','Illustrative per-passenger transfer catalog service.','TRANSPORTATION',25,'USD',24,'ACTIVE',now(),now())
ON CONFLICT (service_code) DO NOTHING;

UPDATE "Service" s SET "service_category_id"=c."service_category_id"
FROM "ServiceCategory" c WHERE
   (s."service_type"=c."category_code" OR
   (s."service_code"='DEMO-SPA' AND c."category_code"='SPA_WELLNESS') OR
   (s."service_code" LIKE 'DEMO-DINING-%' AND c."category_code"='DINING') OR
   (s."service_code" LIKE 'DEMO-BEV-%' AND c."category_code"='BEVERAGE') OR
   (s."service_code" LIKE 'DEMO-INTERNET-%' AND c."category_code"='INTERNET') OR
   (s."service_code" LIKE 'DEMO-SHORE-%' AND c."category_code"='SHORE_EXCURSION') OR
   (s."service_code" LIKE 'DEMO-SPA-%' AND c."category_code"='SPA_WELLNESS') OR
   (s."service_code" LIKE 'DEMO-ACTIVITY-%' AND c."category_code"='ENTERTAINMENT_ACTIVITY') OR
   (s."service_code" LIKE 'DEMO-PHOTO-%' AND c."category_code"='PHOTOGRAPHY') OR
   (s."service_code" LIKE 'DEMO-CABIN-%' AND c."category_code"='CABIN_SERVICE') OR
   (s."service_code" LIKE 'DEMO-RETAIL-%' AND c."category_code"='SHOPPING_RETAIL') OR
   (s."service_code" LIKE 'DEMO-LAUNDRY-%' AND c."category_code"='LAUNDRY') OR
   (s."service_code" LIKE 'DEMO-FAMILY-%' AND c."category_code"='CHILDCARE_FAMILY') OR
   (s."service_code" LIKE 'DEMO-MEDICAL-%' AND c."category_code"='MEDICAL_ASSISTANCE') OR
   (s."service_code" LIKE 'DEMO-TRANSPORT-%' AND c."category_code"='TRANSPORTATION'))
  AND s."service_code" LIKE 'DEMO-%' AND s."service_category_id" IS NULL;

UPDATE "Service" SET "unit_code"='DEVICE', "has_allowance"=true, "applicability_scope"='PASSENGER'
 WHERE "service_code" IN ('DEMO-WIFI','DEMO-INTERNET-WEB','DEMO-INTERNET-STREAM') AND "unit_code" IS NULL;
UPDATE "Service" SET "unit_code"='SESSION', "requires_schedule"=true, "requires_staff"=true, "duration_minutes"=60
 WHERE "service_code" IN ('DEMO-SPA-MASSAGE','DEMO-PHOTO-SESSION') AND "unit_code" IS NULL;
UPDATE "Service" SET "unit_code"='PERSON', "requires_schedule"=true
 WHERE "service_code" IN ('DEMO-DINING-MAIN','DEMO-DINING-SPECIALTY','DEMO-SHORE-CITY','DEMO-SHORE-SNORKEL','DEMO-SPA-YOGA','DEMO-ACTIVITY-SHOW','DEMO-ACTIVITY-COOKING','DEMO-FAMILY-CHILDCARE') AND "unit_code" IS NULL;

INSERT INTO "Product" (product_id,product_code,product_name,description,product_type,unit_price,currency_code,status,created_at,updated_at)
VALUES ('85000000-0000-4000-8000-000000000117','DEMO-RETAIL-SOUVENIR','Demo cruise souvenir','Illustrative retail item; cataloged as a product, not duplicated as a service.','SOUVENIR',18,'USD','ACTIVE',now(),now())
ON CONFLICT (product_code) DO NOTHING;

INSERT INTO "TourPackage" (tour_package_id, package_code, package_name, description, package_type,
                            price, currency_code, status, start_date, end_date, created_at, updated_at)
VALUES
 ('85000000-0000-4000-8000-000000000011', 'DEMO-CONTRACTUAL', 'Demo contractual package',
  'Local demo Basic tier; its entitlements are inherited explicitly through the package parent relation.',
  'BASIC', 0.0000, 'USD', 'ACTIVE', NULL, NULL, now(), now()),
 ('85000000-0000-4000-8000-000000000012', 'DEMO-ADDON', 'Demo premium package',
  'Local demo Premium tier; sample sale channels and cutoff policies are intentionally not represented.',
  'PREMIUM', 25.0000, 'USD', 'ACTIVE', NULL, NULL, now(), now())
ON CONFLICT (package_code) DO NOTHING;

INSERT INTO "TourPackage" (tour_package_id, package_code, package_name, description, package_type,
                            price, currency_code, status, start_date, end_date, created_at, updated_at, cabin_selection_policy)
VALUES
 ('85000000-0000-4000-8000-000000000021','DEMO-PLUS','Demo Plus package','Illustrative CASMS package tier.','PLUS',100,'USD','ACTIVE',NULL,NULL,now(),now(),'LOCATION_PREFERENCE'),
 ('85000000-0000-4000-8000-000000000022','DEMO-PREMIUM-PLUS','Demo Premium Plus package','Illustrative highest CASMS package tier.','PREMIUM_PLUS',250,'USD','ACTIVE',NULL,NULL,now(),now(),'PRIORITY_LOCATION'),
 ('85000000-0000-4000-8000-000000000031','DEMO-ADDON-DINING','Demo specialty dining add-on','Illustrative package add-on.','ADD_ON',45,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000032','DEMO-ADDON-BEVERAGE','Demo beverage add-on','Illustrative package add-on.','ADD_ON',65,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000033','DEMO-ADDON-INTERNET','Demo Internet add-on','Illustrative package add-on.','ADD_ON',22,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000034','DEMO-ADDON-SHORE','Demo shore excursion add-on','Illustrative package add-on.','ADD_ON',75,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000035','DEMO-ADDON-SPA','Demo wellness add-on','Illustrative package add-on.','ADD_ON',90,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000036','DEMO-ADDON-PHOTO','Demo photography add-on','Illustrative package add-on.','ADD_ON',120,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000037','DEMO-ADDON-CABIN','Demo cabin service add-on','Illustrative package add-on.','ADD_ON',40,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN'),
 ('85000000-0000-4000-8000-000000000038','DEMO-ADDON-LAUNDRY','Demo laundry add-on','Illustrative package add-on.','ADD_ON',25,'USD','ACTIVE',NULL,NULL,now(),now(),'AUTO_ASSIGN')
ON CONFLICT (package_code) DO NOTHING;

-- The four demo tiers form an explicit hierarchy; children retain their own package rows.
UPDATE "TourPackage" child SET "parent_package_id"=parent.tour_package_id
FROM (VALUES ('DEMO-PLUS','DEMO-CONTRACTUAL'),('DEMO-ADDON','DEMO-PLUS'),('DEMO-PREMIUM-PLUS','DEMO-ADDON')) v(child_code,parent_code)
JOIN "TourPackage" parent ON parent.package_code=v.parent_code
WHERE child.package_code=v.child_code AND child.parent_package_id IS NULL;

INSERT INTO "TourPackageService" (tour_package_id, service_id, benefit_type)
SELECT p.tour_package_id, s.service_id, links.benefit_type::"TourPackageService_benefit_type"
FROM (VALUES
  ('DEMO-CONTRACTUAL', 'DEMO-WIFI', 'INCLUDED'),
  ('DEMO-CONTRACTUAL', 'DEMO-DINING', 'OPTIONAL'),
  ('DEMO-ADDON', 'DEMO-SPA', 'INCLUDED'),
  ('DEMO-ADDON', 'DEMO-DINING', 'INCLUDED')
) AS links(package_code, service_code, benefit_type)
JOIN "TourPackage" p ON p.package_code = links.package_code
JOIN "Service" s ON s.service_code = links.service_code
ON CONFLICT (tour_package_id, service_id) DO NOTHING;

INSERT INTO "TourPackageService" (tour_package_id, service_id, benefit_type)
SELECT p.tour_package_id, s.service_id, 'INCLUDED'::"TourPackageService_benefit_type"
FROM (VALUES
 ('DEMO-CONTRACTUAL','DEMO-DINING-MAIN'),('DEMO-CONTRACTUAL','DEMO-ACTIVITY-SHOW'),
 ('DEMO-PLUS','DEMO-BEV-NONALCOHOLIC'),('DEMO-PLUS','DEMO-INTERNET-WEB'),('DEMO-PLUS','DEMO-DINING-MAIN'),
 ('DEMO-ADDON','DEMO-INTERNET-STREAM'),('DEMO-ADDON','DEMO-SHORE-CITY'),('DEMO-ADDON','DEMO-SPA-YOGA'),
 ('DEMO-PREMIUM-PLUS','DEMO-BEV-PREMIUM'),('DEMO-PREMIUM-PLUS','DEMO-INTERNET-STREAM'),('DEMO-PREMIUM-PLUS','DEMO-SHORE-SNORKEL'),('DEMO-PREMIUM-PLUS','DEMO-SPA-MASSAGE')
) AS links(package_code, service_code)
JOIN "TourPackage" p ON p.package_code=links.package_code
JOIN "Service" s ON s.service_code=links.service_code
ON CONFLICT (tour_package_id, service_id) DO NOTHING;

INSERT INTO "TourPackageService" (tour_package_id, service_id, benefit_type)
SELECT p.tour_package_id,s.service_id,'OPTIONAL'::"TourPackageService_benefit_type"
FROM (VALUES
 ('DEMO-ADDON-DINING','DEMO-DINING-SPECIALTY'),('DEMO-ADDON-BEVERAGE','DEMO-BEV-PREMIUM'),
 ('DEMO-ADDON-INTERNET','DEMO-INTERNET-STREAM'),('DEMO-ADDON-SHORE','DEMO-SHORE-SNORKEL'),
 ('DEMO-ADDON-SPA','DEMO-SPA-MASSAGE'),('DEMO-ADDON-PHOTO','DEMO-PHOTO-SESSION'),
 ('DEMO-ADDON-CABIN','DEMO-CABIN-CELEBRATION'),('DEMO-ADDON-LAUNDRY','DEMO-LAUNDRY-BAG')
) AS links(package_code,service_code)
JOIN "TourPackage" p ON p.package_code=links.package_code
JOIN "Service" s ON s.service_code=links.service_code
ON CONFLICT (tour_package_id,service_id) DO NOTHING;

INSERT INTO "PackageEntitlement" (tour_package_id, service_category_id, benefit_type, quantity_limit, unit_code, owner_scope)
SELECT p.tour_package_id,c.service_category_id,benefit.benefit_type,benefit.quantity_limit,benefit.unit_code,'PASSENGER'
FROM (VALUES
 ('DEMO-CONTRACTUAL','DINING','LIMITED',3,'MEAL'),('DEMO-PLUS','BEVERAGE','LIMITED',8,'DRINK'),
 ('DEMO-PLUS','INTERNET','LIMITED',1,'DEVICE'),('DEMO-ADDON','SHORE_EXCURSION','DISCOUNT',NULL,'PERSON'),
 ('DEMO-ADDON','SPA_WELLNESS','DISCOUNT',NULL,'SESSION'),('DEMO-PREMIUM-PLUS','DINING','INCLUDED',NULL,'MEAL'),
 ('DEMO-PREMIUM-PLUS','BEVERAGE','LIMITED',12,'DRINK'),('DEMO-PREMIUM-PLUS','INTERNET','LIMITED',2,'DEVICE'),
 ('DEMO-PREMIUM-PLUS','SHORE_EXCURSION','LIMITED',2,'PERSON'),('DEMO-PREMIUM-PLUS','SPA_WELLNESS','LIMITED',1,'SESSION'),
 ('DEMO-PREMIUM-PLUS','PHOTOGRAPHY','DISCOUNT',NULL,'SESSION'),('DEMO-PREMIUM-PLUS','CABIN_SERVICE','DISCOUNT',NULL,'ORDER')
) AS benefit(package_code, category_code, benefit_type, quantity_limit, unit_code)
JOIN "TourPackage" p ON p.package_code=benefit.package_code
JOIN "ServiceCategory" c ON c.category_code=benefit.category_code
ON CONFLICT DO NOTHING;

INSERT INTO "PackageEntitlement" (tour_package_id,service_category_id,benefit_type,quantity_limit,unit_code,owner_scope)
SELECT p.tour_package_id,c.service_category_id,'INCLUDED',benefit.quantity_limit,benefit.unit_code,'PASSENGER'
FROM (VALUES
 ('DEMO-ADDON-DINING','DINING',1,'MEAL'),('DEMO-ADDON-BEVERAGE','BEVERAGE',8,'DRINK'),
 ('DEMO-ADDON-INTERNET','INTERNET',1,'DEVICE'),('DEMO-ADDON-SHORE','SHORE_EXCURSION',1,'PERSON'),
 ('DEMO-ADDON-SPA','SPA_WELLNESS',1,'SESSION'),('DEMO-ADDON-PHOTO','PHOTOGRAPHY',1,'SESSION'),
 ('DEMO-ADDON-CABIN','CABIN_SERVICE',1,'ORDER'),('DEMO-ADDON-LAUNDRY','LAUNDRY',1,'BAG')
) AS benefit(package_code,category_code,quantity_limit,unit_code)
JOIN "TourPackage" p ON p.package_code=benefit.package_code
JOIN "ServiceCategory" c ON c.category_code=benefit.category_code
ON CONFLICT DO NOTHING;

-- Cruise-agnostic sample sessions are intentionally not linked to fake ships/cruises/stops.
INSERT INTO "ServiceSchedule" (schedule_code,service_id,starts_at,ends_at,location,capacity,status)
SELECT v.schedule_code,s.service_id,v.starts_at,v.ends_at,v.location,v.capacity,'OPEN'
FROM (VALUES
 ('DEMO-SPA-MASSAGE-01','DEMO-SPA-MASSAGE',date_trunc('day',now())+interval '1 day 10 hours',date_trunc('day',now())+interval '1 day 11 hours','Demo spa',4),
 ('DEMO-SPA-MASSAGE-02','DEMO-SPA-MASSAGE',date_trunc('day',now())+interval '1 day 11 hours',date_trunc('day',now())+interval '1 day 12 hours','Demo spa',4),
 ('DEMO-COOKING-CLASS-01','DEMO-ACTIVITY-COOKING',date_trunc('day',now())+interval '2 days 14 hours',date_trunc('day',now())+interval '2 days 15 hours','Demo galley studio',16)
) AS v(schedule_code,service_code,starts_at,ends_at,location,capacity)
JOIN "Service" s ON s.service_code=v.service_code
ON CONFLICT (schedule_code) DO NOTHING;

COMMIT;
