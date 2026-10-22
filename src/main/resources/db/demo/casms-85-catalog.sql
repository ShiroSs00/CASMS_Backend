-- Local-only CASMS-85 catalogue sample. Run manually against a disposable/local CASMS database.
-- Safe to rerun: only rows with the reserved DEMO-* business codes are inserted/updated.
BEGIN;

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
ON CONFLICT (service_code) DO UPDATE SET
  service_name = EXCLUDED.service_name,
  description = EXCLUDED.description,
  service_type = EXCLUDED.service_type,
  price = EXCLUDED.price,
  currency_code = EXCLUDED.currency_code,
  capacity = EXCLUDED.capacity,
  status = EXCLUDED.status,
  updated_at = now();

INSERT INTO "TourPackage" (tour_package_id, package_code, package_name, description, package_type,
                            price, currency_code, status, start_date, end_date, created_at, updated_at)
VALUES
 ('85000000-0000-4000-8000-000000000011', 'DEMO-CONTRACTUAL', 'Demo contractual package',
  'Local demo package. Inclusion is explicit in TourPackageService; no tier inheritance is implied.',
  'BASIC', 0.0000, 'USD', 'ACTIVE', NULL, NULL, now(), now()),
 ('85000000-0000-4000-8000-000000000012', 'DEMO-ADDON', 'Demo pre-cruise add-on package',
  'Local demo package. Purchase channels and cutoff are not represented by the current schema.',
  'PREMIUM', 25.0000, 'USD', 'ACTIVE', NULL, NULL, now(), now())
ON CONFLICT (package_code) DO UPDATE SET
  package_name = EXCLUDED.package_name,
  description = EXCLUDED.description,
  package_type = EXCLUDED.package_type,
  price = EXCLUDED.price,
  currency_code = EXCLUDED.currency_code,
  status = EXCLUDED.status,
  updated_at = now();

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
ON CONFLICT (tour_package_id, service_id) DO UPDATE SET benefit_type = EXCLUDED.benefit_type;

COMMIT;
