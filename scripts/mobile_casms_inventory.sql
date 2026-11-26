\set ON_ERROR_STOP on
BEGIN READ ONLY;
DO $body$
BEGIN
    IF current_database() <> 'casms'
       OR inet_server_addr() <> inet '127.0.0.1'
       OR inet_server_port() <> 5432
       OR current_schema() <> 'public' THEN
        RAISE EXCEPTION 'Unexpected PostgreSQL target';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '141' AND success) THEN
        RAISE EXCEPTION 'Expected validated migration V141';
    END IF;
END
$body$;
SELECT jsonb_build_object(
    'database', current_database(),
    'readOnly', current_setting('transaction_read_only'),
    'bookings', (SELECT jsonb_object_agg(status, total) FROM (
        SELECT booking_status::text AS status, count(*) AS total FROM "Booking" GROUP BY 1
    ) AS counts),
    'cruises', (SELECT jsonb_object_agg(status, total) FROM (
        SELECT status::text, count(*) AS total FROM "Cruise" GROUP BY 1
    ) AS counts),
    'passengersWithUser', (SELECT count(*) FROM "Passenger" WHERE user_id IS NOT NULL),
    'cabinAssignments', (SELECT count(*) FROM "CabinAssignment"),
    'checkins', (SELECT count(*) FROM "CruiseCheckin"),
    'cardClaims', (SELECT count(*) FROM "PassengerCardClaim"),
    'cards', (SELECT count(*) FROM "PassengerNfcCard"),
    'terminals', (SELECT count(*) FROM "PosTerminal"),
    'assignments', (SELECT count(*) FROM "AreaStaffAssignment" WHERE enabled),
    'services', (SELECT jsonb_object_agg(currency, total) FROM (
        SELECT coalesce(currency_code, 'missing') AS currency, count(*) AS total FROM "Service" GROUP BY 1
    ) AS counts),
    'serviceRequests', (SELECT count(*) FROM "ServiceRequest"),
    'serviceSchedules', (SELECT count(*) FROM "ServiceSchedule"),
    'activities', (SELECT count(*) FROM "OnboardActivity"),
    'shoreExcursions', (SELECT count(*) FROM "ShoreExcursion"),
    'registrations', (SELECT count(*) FROM "Registration"),
    'onboardAccounts', (SELECT count(*) FROM "OnboardAccount"),
    'spendingLimits', (SELECT count(*) FROM "SpendingLimit"),
    'onboardTransactions', (SELECT count(*) FROM "OnboardTransaction"),
    'settlements', (SELECT count(*) FROM "FinalSettlement"),
    'notifications', (SELECT count(*) FROM "Notification"),
    'ackLinks', (SELECT count(notification_id) FROM "ImpactNotificationOutbox"),
    'ackLinkConstraints', (SELECT jsonb_agg(pg_get_constraintdef(oid) ORDER BY contype)
        FROM pg_constraint WHERE conrelid = '"ImpactNotificationOutbox"'::regclass
          AND (pg_get_constraintdef(oid) LIKE 'UNIQUE (notification_id)%'
              OR pg_get_constraintdef(oid) LIKE 'FOREIGN KEY (notification_id)%'))
);
ROLLBACK;
