-- Read-only audit for CASMS ship identity. Run against the target PostgreSQL database
-- before attempting any code correction or data migration.

-- Exact and case/whitespace-normalized ship-code duplicates.
SELECT ship_code, COUNT(*) AS row_count, ARRAY_AGG(ship_id ORDER BY ship_id) AS ship_ids
FROM "Ship"
GROUP BY ship_code
HAVING COUNT(*) > 1;

SELECT UPPER(BTRIM(ship_code)) AS normalized_ship_code, COUNT(*) AS row_count,
       ARRAY_AGG(ship_id ORDER BY ship_id) AS ship_ids,
       ARRAY_AGG(ship_code ORDER BY ship_code) AS stored_codes
FROM "Ship"
GROUP BY UPPER(BTRIM(ship_code))
HAVING COUNT(*) > 1;

-- Possible duplicate names are investigative leads only, not proof of duplicate ships.
SELECT LOWER(REGEXP_REPLACE(BTRIM(ship_name), '\s+', ' ', 'g')) AS normalized_name,
       COUNT(*) AS row_count,
       ARRAY_AGG(ship_id ORDER BY ship_id) AS ship_ids,
       ARRAY_AGG(ship_code ORDER BY ship_code) AS ship_codes
FROM "Ship"
GROUP BY LOWER(REGEXP_REPLACE(BTRIM(ship_name), '\s+', ' ', 'g'))
HAVING COUNT(*) > 1;

-- Manifest identity collisions and ShipModel identity/version assignments.
SELECT manifest_model_id, COUNT(*) AS row_count,
       ARRAY_AGG(ship_id ORDER BY ship_id) AS ship_ids,
       ARRAY_AGG(ship_code ORDER BY ship_code) AS ship_codes
FROM "Ship"
WHERE manifest_model_id IS NOT NULL
GROUP BY manifest_model_id
HAVING COUNT(*) > 1;

SELECT ship_id, model_key, asset_version, COUNT(*) AS row_count,
       ARRAY_AGG(ship_model_id ORDER BY ship_model_id) AS model_ids
FROM "ShipModel"
WHERE model_key IS NOT NULL
GROUP BY ship_id, model_key, asset_version
HAVING COUNT(*) > 1;

-- Sequence state compared with the largest currently stored CASMS ship code.
SELECT last_value AS sequence_last_value, is_called,
       COALESCE((SELECT MAX(SUBSTRING(ship_code FROM '^SH-([0-9]{4})$')::integer)
                 FROM "Ship" WHERE ship_code ~ '^SH-[0-9]{4}$'), 0) AS max_stored_ship_code
FROM casms_ship_code_seq;
