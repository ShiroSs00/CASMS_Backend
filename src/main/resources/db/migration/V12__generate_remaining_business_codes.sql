-- Add generators for the remaining CASMS-managed identifiers. Existing values
-- are retained; each sequence resumes after its highest already-conforming code.
CREATE SEQUENCE IF NOT EXISTS casms_activity_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_service_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_product_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_package_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_excursion_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_tour_group_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;

SELECT setval('casms_activity_code_seq', COALESCE(MAX(substring(activity_code from '^AC-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE activity_code ~ '^AC-[0-9]{4}$') > 0)
FROM "OnboardActivity";
SELECT setval('casms_service_code_seq', COALESCE(MAX(substring(service_code from '^SV-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE service_code ~ '^SV-[0-9]{4}$') > 0)
FROM "Service";
SELECT setval('casms_product_code_seq', COALESCE(MAX(substring(product_code from '^PR-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE product_code ~ '^PR-[0-9]{4}$') > 0)
FROM "Product";
SELECT setval('casms_package_code_seq', COALESCE(MAX(substring(package_code from '^PK-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE package_code ~ '^PK-[0-9]{4}$') > 0)
FROM "TourPackage";
SELECT setval('casms_excursion_code_seq', COALESCE(MAX(substring(excursion_code from '^EX-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE excursion_code ~ '^EX-[0-9]{4}$') > 0)
FROM "ShoreExcursion";
SELECT setval('casms_tour_group_code_seq', COALESCE(MAX(substring(group_code from '^TG-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE group_code ~ '^TG-[0-9]{4}$') > 0)
FROM "TourGroup";

-- Excursion code was nullable. Backfill missing values without changing any
-- existing supplier or legacy code; then require a code for future records.
UPDATE "ShoreExcursion"
SET excursion_code = 'EX-' || lpad(nextval('casms_excursion_code_seq')::text, 4, '0')
WHERE excursion_code IS NULL OR btrim(excursion_code) = '';

ALTER TABLE "ShoreExcursion" ALTER COLUMN excursion_code SET NOT NULL;
