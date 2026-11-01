-- Some existing databases have a Hibernate-created VARCHAR check that predates
-- the PLUS, PREMIUM_PLUS and ADD_ON tiers already supported by the application.
-- Extend that exact legacy check without changing existing rows or other rules.
DO $$
DECLARE legacy_expression text;
BEGIN
    SELECT pg_get_expr(conbin,conrelid) INTO legacy_expression
    FROM pg_constraint
    WHERE conrelid='"TourPackage"'::regclass AND conname='TourPackage_package_type_check' AND contype='c';
    IF legacy_expression IS NOT NULL THEN
        ALTER TABLE "TourPackage" DROP CONSTRAINT "TourPackage_package_type_check";
        EXECUTE format('ALTER TABLE "TourPackage" ADD CONSTRAINT "TourPackage_package_type_check" CHECK ((%s) OR package_type::text IN (''PLUS'',''PREMIUM_PLUS'',''ADD_ON''))',legacy_expression);
    END IF;
END $$;
