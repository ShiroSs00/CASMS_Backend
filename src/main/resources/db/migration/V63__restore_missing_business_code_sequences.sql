-- Older databases applied unrelated V11/V12 migrations before business-code
-- generators were introduced. Restore only missing generators without editing
-- migration history, existing codes or the state of existing sequences.
DO $$
DECLARE
    generator record;
    highest_code bigint;
    code_pattern text;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('casms_restore_business_code_sequences'));

    FOR generator IN
        SELECT * FROM (VALUES
            ('Ship', 'ship_code', 'SH', 'casms_ship_code_seq'),
            ('Cruise', 'cruise_code', 'CR', 'casms_cruise_code_seq'),
            ('Booking', 'booking_reference', 'BK', 'casms_booking_code_seq'),
            ('OperationalIssue', 'issue_code', 'OI', 'casms_operational_issue_code_seq'),
            ('OperationalRegulation', 'regulation_code', 'RG', 'casms_operational_regulation_code_seq'),
            ('OnboardActivity', 'activity_code', 'AC', 'casms_activity_code_seq'),
            ('Service', 'service_code', 'SV', 'casms_service_code_seq'),
            ('Product', 'product_code', 'PR', 'casms_product_code_seq'),
            ('TourPackage', 'package_code', 'PK', 'casms_package_code_seq'),
            ('ShoreExcursion', 'excursion_code', 'EX', 'casms_excursion_code_seq'),
            ('TourGroup', 'group_code', 'TG', 'casms_tour_group_code_seq')
        ) AS generators(table_name, column_name, prefix, sequence_name)
    LOOP
        IF to_regclass(format('public.%I', generator.sequence_name)) IS NULL THEN
            -- Keep the code snapshot stable until the new sequence is visible.
            EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE', generator.table_name);
            EXECUTE format(
                'CREATE SEQUENCE public.%I MINVALUE 1 MAXVALUE 9999 NO CYCLE',
                generator.sequence_name
            );
            code_pattern := '^' || generator.prefix || '-([0-9]{4})$';
            EXECUTE format(
                'SELECT COALESCE(MAX(substring(%I from %L)::bigint), 0) FROM public.%I',
                generator.column_name, code_pattern, generator.table_name
            ) INTO highest_code;
            PERFORM setval(
                to_regclass(format('public.%I', generator.sequence_name)),
                greatest(highest_code, 1),
                highest_code > 0
            );
        END IF;
    END LOOP;
END
$$;
