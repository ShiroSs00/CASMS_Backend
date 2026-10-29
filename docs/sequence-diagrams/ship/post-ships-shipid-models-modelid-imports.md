# POST /ships/{shipId}/models/{modelId}/imports

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

`decksImported` now includes passenger and technical/service levels. `totalObjects` remains decks + serviceDecks + cabins; ignored business-service nodes are not counted. Existing manual DeckServiceAssignment rows are untouched. Import does not publish the model; READY/ARCHIVED remains immutable.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipModelController
    participant Service as ShipModelImportService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /ships/{shipId}/models/{modelId}/imports
    Note over Security,Controller: Handler permission hasAuthority('SHIP_UPDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: importStructure with bound parameters
        Controller->>Service: importService.importModel()
        Service->>Service: ModelImportJobLifecycle.start in REQUIRES_NEW
        Service->>Repository: Load model scoped to ship, require DRAFT, saveAndFlush RUNNING job
        Repository->>DB: Commit job independently
        Note over Service,DB: TransactionTemplate starts atomic structure transaction
        Service->>Repository: ShipModelRepository.lockShip and reload model
        Repository->>DB: Lock ship and verify model still DRAFT
        Service->>DB: ShipModelAssetStore reads immutable manifest and GLB JSON from Large Objects
        Service->>Service: Check revision, node identities and manifest business identities
        loop Passenger decks and serviceDecks
            Service->>Service: Validate number, name and elevation against GLB extras
            Note over Service: Passenger decks positive within limit; technical decks non-positive
            Service->>Repository: Resolve Deck by ship and number, save name, level, elevation and raw deck metadata
            Repository->>DB: INSERT or UPDATE Deck, reuse stable deck UUID
            Service->>Repository: Upsert DECK ShipModelObject linked to real deckId
            Repository->>DB: Persist model mapping and metadata
        end
        Note over Service: Model service and area objects are ignored, no catalogue creation
        loop Existing cabin import workflow
            Service->>Repository: Resolve passenger deck and cabin; save cabin and mapping
            Repository->>DB: Persist only valid business cabin data
        end
        alt Object validation issue
            Service->>Repository: Save ModelImportIssue and increment failed count
            Repository->>DB: Preserve valid objects, final status PARTIAL or FAILED
        else All counted objects imported
            Service->>Repository: Save finished job with SUCCEEDED
        end
        Note over Service,DB: Commit job counters and structure atomically
        opt Unexpected persistence or structural failure
            Note over Service,DB: Roll back all structure changes
            Service->>Service: ModelImportJobLifecycle.fail in REQUIRES_NEW
            Service->>Repository: Persist FAILED job and finishedAt separately
            Service-->>Controller: Rethrow, mapped 400, 404, 409 or 500 according to failure
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 201 ApiResponse of ModelImportJobResponse
    end
```
