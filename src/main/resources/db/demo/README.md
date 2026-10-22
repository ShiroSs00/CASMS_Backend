# CASMS-85 local demo catalogue

`casms-85-catalog.sql` is an opt-in local sample, not a Flyway migration. It only writes records with `DEMO-*` business codes and can be run repeatedly. Do not execute it against production. Its USD prices and capacity values are illustrative test data, not OceVie prices or approved policy.

Run it only after the normal Flyway migrations have completed, for example:

```powershell
psql "$env:LOCAL_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/demo/casms-85-catalog.sql
```

The script provides three service catalogue rows, two active packages and explicit package/service benefits. The current schema does not model package sales channels, cutoff rules, entitlement quotas, service sessions, staff shifts/assignments, or a booking-to-contract snapshot. Therefore this sample is deliberately limited to a linked catalogue; it does not create fake staff, passengers, bookings, payments, guidance attendance, or check-in records and does not by itself satisfy the full end-to-end demo scenario.

Verify the catalogue links with:

```sql
SELECT p.package_code, s.service_code, ps.benefit_type
FROM "TourPackageService" ps
JOIN "TourPackage" p USING (tour_package_id)
JOIN "Service" s USING (service_id)
WHERE p.package_code LIKE 'DEMO-%'
ORDER BY p.package_code, s.service_code;
```
