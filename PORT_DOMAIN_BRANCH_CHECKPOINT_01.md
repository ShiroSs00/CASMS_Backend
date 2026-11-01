# Port domain branch checkpoint — 2026-11-01

Published as a work-in-progress branch at the Product Owner's renewed request to run BE tests and push both repositories on separate branches. This is not a merge/release approval.

Branch in both repositories: `codex/port-domain-integration-20261101`.

Validation:
- FE typecheck, lint and production build: exit 0 in the preceding verification pass, with no subsequent production-code changes.
- FE full tests: 642 tests, 641 pass, 0 fail, 1 skipped.
- BE targeted Port/ITN/contract/checksum tests: 77 tests, 0 failures/errors.
- BE expanded non-database tests rerun 2026-11-01 18:41 Asia/Saigon: 472 tests, 25 failures, 15 errors, 0 skipped; Maven exit 1.
- These unresolved failures span Booking/Cruise fixtures, legacy template-review assertions, incomplete itinerary/program fixtures, duration finalization and permission allowlist coverage. Do not describe this checkpoint as all tests passing.
- No database migration, live DB integration or production mutation was executed. V75–V79 require a separately authorized deployment/test-database pass.

All source/test changes already in the local FE/BE working trees are included, as requested. Environment files, IDE/machine configuration, archives, generated OpenAPI exports and temporary logs/caches are excluded and preserved locally.

Backend branch is based on fa8eeaf1b5b578e954ccecb2c64d8e3e8e3e49ba. Remote main has two newer CASMS-91 commits; no merge/rebase was performed in the dirty workspace. Reconcile those changes before merging this checkpoint.

Next gate: fix the remaining BE failures, rerun the full non-database set, then validate migrations/integration against an isolated test database. Do not automatically merge, deploy, Open Sale or mark UX acceptance complete.
