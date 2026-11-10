# CASMS-92 validation evidence

2026-11-10. PASS: 67 distinct tests across 10 classes, 0 failures, 0 errors, 0 skipped.

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| BookingAtomicPostgresTest | 3 | 0 | 0 | 0 |
| BookingPostgresIntegrationTest | 6 | 0 | 0 | 0 |
| BookingRecoveryTest | 8 | 0 | 0 | 0 |
| BookingServiceTest | 14 | 0 | 0 | 0 |
| BookingEmailTest | 6 | 0 | 0 | 0 |
| PaymentServiceTest | 9 | 0 | 0 | 0 |
| SepayServiceTest | 16 | 0 | 0 | 0 |
| PassengerItineraryServiceTest | 2 | 0 | 0 | 0 |
| BookingIdempotencyTest | 2 | 0 | 0 | 0 |
| IdempotencyCanonicalPayloadTest | 1 | 0 | 0 | 0 |

Validation commands:

- `mvn -B clean -Dtest=BookingServiceTest,BookingRecoveryTest,BookingIdempotencyTest,BookingAtomicPostgresTest,BookingPostgresIntegrationTest,IdempotencyCanonicalPayloadTest,PaymentServiceTest,BookingEmailTest,PassengerItineraryServiceTest,SepayServiceTest test`: 66 tests PASS before the additional signed-in guest recovery case.
- `mvn -B -Dtest=BookingRecoveryTest,BookingServiceTest test`: final affected-path recheck, 22 tests PASS including the added signed-in guest case. Counts above are distinct cases, not the sum of repeated runs.
- `python scripts/sequence_docs.py --check`: PASS, 605 mappings and diagram coverage/structure. Changed end-to-end behavior was separately reviewed against code in CASMS-92-BOOKING-FLOWS.md; unchanged generated branches are not claimed verified.
- `git diff --check` and staged diff check: PASS.

Runtime: Oracle JDK 21.0.11, Maven 3.9.16, Spring Boot 3.3.4. No Maven Wrapper exists in this checkout.
Nine integration cases used PostgreSQL 16.15 in a disposable Docker container restricted to localhost, with both the test-specific and Spring datasource overrides set explicitly. Flyway V1 through V94 applied successfully to the disposable database (92 migration files). The container was stopped and automatically removed after validation. Application/production PostgreSQL version is still UNKNOWN.

Evidence includes PostgreSQL rollback of business write plus reservation after injected completion failure, successful retry, simultaneous guest requests executing only once, single-use password setup, API owner isolation, stable account-to-passenger linkage across trips, mismatched identity rejection, verified financial projection/refunds, SePay booking/payment state separation and SMTP retry.

Limits: no live Gmail/SMTP delivery, frontend activation screen or shared/production migration deployment was performed. SMTP is disabled by default. Configure deployment secrets and connect the frontend as described in INTEGRATION.md. Legacy uncertain idempotency outcomes and duplicate normalized emails/guest keys require manual reconciliation; legacy guest accounts without setup markers require verified support-assisted onboarding rather than an unsafe password reset.
