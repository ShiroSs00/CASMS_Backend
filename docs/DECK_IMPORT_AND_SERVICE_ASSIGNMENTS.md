# Deck import and manual service assignments

## Import behavior

Upload the existing versioned ZIP through `POST /api/ship-model-assets`, register its model, then call `POST /api/ships/{shipId}/models/{modelId}/imports` while the model is DRAFT.

The importer persists every valid passenger `deck` and technical `serviceDeck` as a `Deck` with a UUID, ship relationship, source number, display name, elevation and complete source deck JSON (`modelMetadataJson`). The existing cabin import remains in place. Areas, restaurants, bars and other service objects inside the GLB are deliberately ignored; no catalogue service is created or changed.

Technical levels such as -1 and 0 use `serviceLevel=true`; positive passenger decks use false. The original positive numbering invariant remains for passenger decks and cabins. `Ship.totalDecks` continues to describe passenger deck count, with `deckNumberLimit` controlling its maximum number. Ship limit checks count passenger decks only. Lists and physical deck totals include technical levels.

Reimport resolves decks by ship and source number and model mappings by model and node key. It reuses deck IDs and does not touch manual service assignments. Per-object issues produce PARTIAL or FAILED jobs; a persistence failure rolls back the whole structure transaction and leaves a separately committed FAILED job. Import does not activate or mutate a READY model.

For previously imported DRAFT models, rerun import to materialize technical deck rows that were formerly only `ShipModelObject` entries. Published model versions remain immutable; register and import a new asset version rather than forcing a READY model back to DRAFT. Existing model-only level data remains readable for backward compatibility.

## API for assigning existing services

### CASMS 90 reuse and ship scope

CASMS-90 reuses the three existing GET/PUT/DELETE routes below; no duplicate endpoints, entity changes or new migration are needed. All three accept optional UUID query parameter `shipId`, for example `PUT /api/decks/{deckId}/services/{serviceId}?shipId={shipId}` with no body. When supplied, the Ship must exist and own the Deck; absent parents or a deck from another ship return 404 without changing the relationship. Invalid path/query UUIDs return 400. When omitted, the Deck's mandatory ship foreign key remains authoritative, preserving existing clients. This scope parameter validates client context; it is not a new staff-to-ship authorization scheme.

PUT is the existing idempotent association update: it establishes the unique pair or returns the same assignment. There are no mutable location/price/status fields on the association. Catalogue changes appear on subsequent reads; changing the selected service means unassigning the previous pair and assigning the new one. Service lifecycle and physical assignment remain separate: existing catalogue entries, including INACTIVE entries, can be linked without activation or sale. This behavior is retained rather than introducing new eligibility policy. DELETE now locks the service row in the same deck-then-service order as PUT.

There was no direct deck-to-service API before this change. Service schedules contain free-text `location`; package service membership is a different relationship. Neither is used as a deck assignment.

| Method and path | Result | Required existing permissions |
| --- | --- | --- |
| `GET /api/decks/{deckId}/services` | 200 with assignment list, including catalogue name, code, type and status | DECK_READ and SERVICE_READ |
| `PUT /api/decks/{deckId}/services/{serviceId}` | 200 with new or existing assignment; no body | DECK_READ and SERVICE_UPDATE |
| `DELETE /api/decks/{deckId}/services/{serviceId}` | 204, removes only the relationship | DECK_READ and SERVICE_UPDATE |

Use a `deckId` returned by `GET /api/decks/by-ship/{shipId}` and an existing `serviceId` from the service catalogue. The ACTIVITY_SERVICE_MANAGER role already has the required permissions; no RBAC permissions were added. Both passenger and technical deck UUIDs are accepted. Linking an inactive service does not activate it or enable a sale.

PUT is idempotent and serializes writes by locking the deck and service rows. A database unique constraint prevents duplicate `(deck_id, service_id)` relationships. A service can be assigned to several decks and several services can be assigned to one deck. Missing parent IDs return 404. DELETE is idempotent when the relationship is absent but both parents exist. Foreign keys prevent removal of referenced decks or services until assignments are removed; constraint errors return 409. Responses use the existing ApiResponse envelope, except for the bodyless 204 response.

## Migration and verification

`V66__persist_service_decks_and_manual_service_assignments.sql` adds deck level classification, source metadata and the manual association table. Existing rows retain `service_level=false`; no existing service or asset is deleted or rewritten by the migration. Apply it through Flyway.

The end-to-end request paths are documented in the [import sequence](sequence-diagrams/ship/post-ships-shipid-models-modelid-imports.md) and [manual assignment sequence](sequence-diagrams/ship/put-decks-deckid-services-serviceid.md). Structural coverage is checked with `python scripts/sequence_docs.py --check`; it does not replace source review or runtime tests.

Verified on 2026-10-29 using the separate PostgreSQL database `casms_decks_20261029_86`:

- `mvn -q "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/casms_decks_20261029_86?stringtype=unspecified" "-Dtest=DeckModelFieldsImmutableTest,ShipServiceTest,CabinModelFieldsImmutableTest,ShipModelImportPostgresIntegrationTest" test`: 21 tests, zero failures/errors/skips; 11 use the real PostgreSQL import workflow.
- The committed Zaandam bundle imports 12 physical decks, 291 cabins and 303 model mappings; repeated imports preserve counts. Smaller fixtures verify ignored model services, technical deck UUIDs, multiple model versions, manual assignment idempotency/concurrency, authorization, not-found handling and transaction rollback.
- Flyway migration and Hibernate schema validation succeeded on that database; explicit Flyway validation passed with 64 versioned migrations, including V66.
- `mvn -q -DskipTests package`: succeeded; packaged Spring Boot JAR generated.
- `python scripts/sequence_docs.py --check`: passed for 478 registered routes. Twenty-five affected endpoint diagrams plus the end-to-end flow were reviewed for this change.

The full unrelated application test suite and production deployment were not run. Migration V66 must be applied through the application's Flyway startup before using the new endpoints on another environment.

## CASMS 90 verification

Branch `feature/CASMS-90-deck-service-assignment` starts from `origin/main` commit `5937991`. CASMS-90 changes only the existing assignment controller/service, their tests and documentation. It reuses the V66 entity, table, uniqueness constraint and foreign keys; no additional migration is necessary. Importer, cabin geometry, model assets, frontend and RBAC definitions are unchanged.

The following command completed successfully against the existing isolated PostgreSQL test database on 2026-10-29:

```powershell
mvn -q "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/casms_decks_20261029_86?stringtype=unspecified" "-Dtest=DeckServiceAssignmentServiceTest,DeckServiceAssignmentPostgresIntegrationTest,ShipModelImportPostgresIntegrationTest,DeckModelFieldsImmutableTest,ShipServiceTest,CabinModelFieldsImmutableTest,ShipReadinessAssessmentTest" test
```

Results: 47 tests, zero failures, errors or skips. The 24 new tests cover optional ship scope, invalid UUIDs, unknown ship/deck/service, wrong-ship read/write/delete, each missing permission group, idempotent PUT and DELETE, catalogue updates reflected in reads, retaining the original Service, and independent assignments across decks. Existing PostgreSQL import tests also verify concurrent assignment uniqueness, reimport preservation, ignored model services and rollback. Flyway validation still passes for the existing 64 migrations. `python scripts/sequence_docs.py --check` passes for all 478 routes; the three changed endpoint diagrams and their end-to-end flow were reviewed against source.

No new lifecycle restriction is assumed: an inactive catalogue service may remain physically assigned, as before, without activation or permission to sell it. Requiring only ACTIVE services would be a separate business-policy change. Production deployment and the unrelated full test suite are outside this verification scope.
