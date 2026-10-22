# CASMS-85 local demo catalogue

`casms-85-catalog.sql` is an opt-in local sample, not a Flyway migration. It only writes records with `DEMO-*` business codes and can be run repeatedly. The Spring `dev` and `demo` profiles run it after Flyway at application startup; the default and `prod` profiles do not. Never execute it against production. Its USD prices, package benefits, and capacity values are illustrative test data, not OceVie prices or approved policy.

Start with catalog seed enabled:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

For an isolated demonstration database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` before starting. The seed inserts one isolated demo ship, deck and four cabins (one per cabin type), 13 service categories, 23 sample services, one retail `Product`, four main package tiers, eight add-on packages, category entitlements, explicit package/service links, and three generic sample sessions. These records and prices are illustrative only.

Run it only after the normal Flyway migrations have completed, for example:

```powershell
psql "$env:LOCAL_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/demo/casms-85-catalog.sql
```

The seed deliberately does not fabricate ships, cabins, ports, cruise stops, passengers, bookings, contracts, payments, suppliers, or staff. Shore-excursion sessions must be created against real cruise stops; bundled sessions are cruise-agnostic spa/entertainment examples only. The model supports service schedules, request price snapshots, usage, and additional charges, but cutoff and cancellation policies, staff shifts, and booking-to-contract entitlement snapshots remain outside the implemented flow and require confirmed business rules.

Verify the catalogue links with:

```sql
SELECT p.package_code, s.service_code, ps.benefit_type
FROM "TourPackageService" ps
JOIN "TourPackage" p USING (tour_package_id)
JOIN "Service" s USING (service_id)
WHERE p.package_code LIKE 'DEMO-%'
ORDER BY p.package_code, s.service_code;
```
