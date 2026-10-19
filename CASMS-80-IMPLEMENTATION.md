# CASMS-80 implementation review

## Schema findings

The 12 entities, repositories, and checked-in table definitions are present in the repository. This review uses `src/main/resources/db/migration/V1__initial_schema.sql` as the schema source; no live database was inspected. Relevant constraints/comments include: one package assignment per booking passenger using the booking passenger ID as the shared key; package components use composite keys; registration targets exactly one onboard activity or shore excursion; participation is one-to-one with registration; feedback's `status` is an unconstrained `varchar(50)` with no documented status vocabulary; and package pricing is a snapshot whose inclusion in booking fare is explicitly unresolved.

## Status by table

| Table | Implemented | Limits / follow-up |
|---|---|---|
| Service | DTO-backed catalogue CRUD, unique code and price/currency/capacity checks, activate/suspend, deletion guarded by requests, package links and feedback, capacity-based availability | No schedule fields/relationship exist; schedule-check returns 501. Capacity is interpreted as aggregate outstanding request quantity (REQUESTED/ACCEPTED/IN_PROGRESS) because there is no schedule window. |
| ServiceRequest | Create/list/detail, pending update, assignment to authenticated staff user, accept/reject/start/complete/cancel transitions, completion timestamp, passenger ownership for passenger routes | No charge or scheduled execution flow was invented. Capacity uses the Service field as documented above. |
| Product | DTO-backed CRUD, unique code and price/currency validation, activate/discontinue, explicit unsupported sale response | No Product sale/POS relation or product transaction line exists; sale returns 501. |
| Feedback | No write/read workflow added | Migration documents service, completed activity/excursion registration, and general voyage targets, but no target check, rating bounds, duplicate policy, or status vocabulary/moderation states are defined. Implementing submission/review now would require guessing required `status`; resolve status vocabulary and rating policy first. Permission constants are retained for compatibility but no role grants or endpoints are wired. |
| TourPackage | Draft create/update, list/detail, code/price/date checks, component/status validation on activation, deactivate, benefits summary, deletion of unused draft plus its component links | Dates are checked against the full cruise range when assigning. Confirmed booking assignments cannot be changed or removed because pricing history/fare treatment is unresolved. |
| BookingPassengerPackage | Assign/read/list/change/remove while booking is DRAFT; one-to-one shared key preserved; owner/booking/package/date checks; row locks prevent duplicate assignment races; agreed price/currency snapshot from package | No confirmed-booking package upgrade or refund/fare adjustment is modeled. |
| TourPackageService | List/add/update benefit/remove using typed composite key; duplicate/reference and package edit checks | Changes limited to unassigned DRAFT packages. |
| TourPackageActivity | List/add/update benefit/remove using typed composite key; duplicate/reference and package edit checks | Changes limited to unassigned DRAFT packages. |
| TourPackageExcursion | List/add/update benefit/remove using typed composite key; duplicate/reference and package edit checks | Changes limited to unassigned DRAFT packages. |
| OnboardActivity | DTO-backed CRUD, schedule overlap validation for same cruise/location, availability, open/start/complete/cancel transitions, registration listing | Same-location overlap is treated as a conflict. `closeRegistration` remains unsupported because the current enum has no CLOSED state. No registration-window fields exist. |
| Registration | Passenger-owned create/list/detail/update-pending/confirm/cancel for onboard activities and shore excursions; duplicate prevention, booking/cruise validation and target row locks for capacity | No deadline/window was added. Confirmation creates participation only if absent. |
| Participation | Staff-protected list/detail, check-in, checkout, complete and no-show with row lock and transition checks | Check-in requires the target activity/excursion to be IN_PROGRESS; no-show is after the target end/return time. |

## API and authorization

Added endpoints under `/servicepackage` for services, products, packages, service requests, package assignments, package benefits, and package-component links; activity and attendance endpoints under `/activities`, plus excursion registration. Controllers use request/response records instead of exposing JPA entities. Existing all-record list behavior is retained.

Added permissions for services, requests, products, packages, assignments, package components and activity lifecycle. Controller method checks are paired with ownership verification for passenger-created service requests, package assignments and registrations. Role initialization merges configured grants with existing grants instead of replacing them. Feedback permission constants remain but are not assigned because no feedback endpoint is implemented.

## Database/configuration

V1 was not edited. Development explicitly uses Hibernate `update`; test disables Flyway and uses `create-drop` against `casms_test`; production uses `validate`. The test profile no longer enables `clean-on-validation-error`. Root `application.yml` retains its pre-existing whitespace-only edit. No migration is proposed for this implementation; product sales, confirmed-booking price changes, activity registration closure and feedback status policy need domain decisions before schema changes.

## Verification

Java 21 and Maven 3.9.16 are installed; there is no Maven wrapper. Final verification passed with the exact commands `mvn -q -DskipTests compile` and `mvn -q test` (both required Maven repository access). The full suite completed successfully: 99 tests, 0 failures, 0 errors, 0 skipped. Tests use Mockito/unit scope; no live or shared database was used. `git diff --check` passed; Git reported only line-ending normalization warnings.
