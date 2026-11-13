-- CASMS-75 branch, extends existing TourPackage* relations without replacing them.
ALTER TABLE "TourPackageActivity"
 ADD COLUMN quantity_limit integer,
 ADD COLUMN unit_code varchar(30),
 ADD COLUMN owner_scope varchar(20) NOT NULL DEFAULT 'PASSENGER',
 ADD CONSTRAINT tour_package_activity_limit_check CHECK(quantity_limit IS NULL OR quantity_limit > 0),
 ADD CONSTRAINT tour_package_activity_owner_check CHECK(owner_scope IN ('PASSENGER','CABIN','BOOKING','GROUP'));
ALTER TABLE "TourPackageExcursion"
 ADD COLUMN quantity_limit integer,
 ADD COLUMN unit_code varchar(30),
 ADD COLUMN owner_scope varchar(20) NOT NULL DEFAULT 'PASSENGER',
 ADD CONSTRAINT tour_package_excursion_limit_check CHECK(quantity_limit IS NULL OR quantity_limit > 0),
 ADD CONSTRAINT tour_package_excursion_owner_check CHECK(owner_scope IN ('PASSENGER','CABIN','BOOKING','GROUP'));
-- Existing TourPackageService.quantity_limit, unit_code, owner_scope come from V20.
-- This VIEW re-checks live port/supplier statuses: staging VERIFIED is not enough.
CREATE VIEW "OperationalVerifiedTour" AS
 SELECT t.tour_code,t.tour_name,t.port_region,t.duration_minutes,t.capacity,
        t.reference_price_vnd,t.verified_port_id,t.verified_supplier_id,
        t.reviewed_by,t.reviewed_at
 FROM "OperationalSeedTour" t
 JOIN "Port" p ON p.port_id=t.verified_port_id
 JOIN "Supplier" s ON s.supplier_id=t.verified_supplier_id
 WHERE t.status='VERIFIED' AND p.status::text='ACTIVE'
   AND trim(p.country_code)='VN' AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
   AND s.status::text='ACTIVE' AND s.verification_status::text='VERIFIED';
-- A verified staging tour is still NOT an approved live ShoreExcursion or an offer.
