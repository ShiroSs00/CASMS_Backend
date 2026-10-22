-- Preserve all existing business codes. New codes are allocated from independent,
-- non-cycling sequences, so concurrent creates cannot reuse a code after deletion.
CREATE SEQUENCE IF NOT EXISTS casms_ship_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_cruise_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_booking_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_operational_issue_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS casms_operational_regulation_code_seq MINVALUE 1 MAXVALUE 9999 NO CYCLE;

SELECT setval('casms_ship_code_seq', COALESCE(MAX(substring(ship_code from '^SH-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE ship_code ~ '^SH-[0-9]{4}$') > 0)
FROM "Ship";
SELECT setval('casms_cruise_code_seq', COALESCE(MAX(substring(cruise_code from '^CR-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE cruise_code ~ '^CR-[0-9]{4}$') > 0)
FROM "Cruise";
SELECT setval('casms_booking_code_seq', COALESCE(MAX(substring(booking_reference from '^BK-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE booking_reference ~ '^BK-[0-9]{4}$') > 0)
FROM "Booking";
SELECT setval('casms_operational_issue_code_seq', COALESCE(MAX(substring(issue_code from '^OI-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE issue_code ~ '^OI-[0-9]{4}$') > 0)
FROM "OperationalIssue";
SELECT setval('casms_operational_regulation_code_seq', COALESCE(MAX(substring(regulation_code from '^RG-([0-9]{4})$')::integer), 1),
              COUNT(*) FILTER (WHERE regulation_code ~ '^RG-[0-9]{4}$') > 0)
FROM "OperationalRegulation";
