UPDATE "Ship"
SET operational_profile = CASE
    WHEN operational_profile ? 'classificationDate'
        THEN operational_profile - 'lastClassificationDate'
    ELSE (operational_profile - 'lastClassificationDate')
        || jsonb_build_object('classificationDate', operational_profile -> 'lastClassificationDate')
END
WHERE operational_profile ? 'lastClassificationDate';
