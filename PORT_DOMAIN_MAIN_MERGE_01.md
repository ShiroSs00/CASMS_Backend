# BE main conflict resolution — 01/11/2026

Branch: `codex/port-domain-integration-20261101`.
Merged base: `origin/main` at `25411b6` (CASMS-91 POS device identity/scoped staff changes).

## Resolution

- RolePermissionInitializer keeps main's POS device and scoped staff model and all four Port Window grants for OPS. Existing manual OPS grants remain additive; empty role grant sets are handled safely. CRM gains no Window write/verify permission.
- Checksum manifest includes both main's V75/V76 migrations and the Port migrations.
- Main V75__pos_device_roles_and_staff_resource_assignments.sql and V76__extend_legacy_package_tier_check.sql are byte-equivalent to origin/main (ignoring checkout line endings).
- Two Port migrations were renamed without changing SQL:
  - V75__preserve_materialized_source_metadata.sql → V80__preserve_materialized_source_metadata.sql.
  - V76__port_directory_ops_permission_groundwork.sql → V81__port_directory_ops_permission_groundwork.sql.
- V77/V78/V79 retain their versions. No duplicate migration versions remain.

## Applied-history check before renumbering

Read-only SQL through local container `casms-postgres`, with `PGOPTIONS=-c default_transaction_read_only=on` and explicit `BEGIN READ ONLY`. Inspected `flyway_schema_history` in all seven local CASMS databases. No V75–V81 records existed in any of them; the runtime database named in the latest JDBC log, `casms_local_20261025`, had V74 applied. This establishes the local pending state, not the state of unconnected remote environments. No history repair, DB write or migration execution occurred.

The first automatic approval review blocked renumbering because applied state was not yet verified. After the PO requested Flyway inspection and the read-only evidence above was obtained, the resolution was approved and executed.

## Validation

- Production compile: BUILD SUCCESS.
- Added regression checks for duplicate Flyway versions and merged OPS/CRM/POS grants.
- Targeted tests: **68 run, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**.
- Command: `mvn -f .env.merge-validation-pom.xml -Dtest=MigrationChecksumLockTest,RolePermissionInitializerTest,PosPrincipalTest,PosRequestValidationTest,IdentityServiceTest,PortWindowEvaluatorTest,ScheduleMaterializationServiceTest,TripDurationContractTest,PortLocalTimeTransitionTest test`.
- A clean attempt could not delete an actively used JAR. Validation used an ignored temporary POM, identical dependencies/source with a separate `target/merge-validation` output directory; the running Backend was not stopped.
- No source conflict markers or unmerged Git paths remain. SQL of both renamed Port migrations is unchanged from the pre-merge branch.
- The earlier expanded-suite failures are not claimed fixed by this conflict-resolution task. Database integration/live E2E remain unrun. Conflict-free does not certify release acceptance.

Only the BE feature branch is updated. Main is not merged/pushed by this task. Existing .idea changes, generated exports, archives and local tools are preserved outside the commit.
