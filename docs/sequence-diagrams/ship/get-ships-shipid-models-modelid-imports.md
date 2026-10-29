# GET /ships/{shipId}/models/{modelId}/imports

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Job counters include real passenger and technical Deck imports. Validation issues distinguish invalid service-deck numbering, metadata mismatch and deck classification conflicts. Ignored service objects are not failed imports. History is not a live database recount.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipModelController
    participant Service as ShipModelImportService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: GET /ships/{shipId}/models/{modelId}/imports
    Note over Security,Controller: Handler permission hasAuthority('SHIP_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: importHistory with bound parameters
        Controller->>Service: importService.history()
        Note over Service,DB: Read-only transaction
        Service->>Repository: Validate ship-scoped model, find jobs by model ordered by startedAt descending
        Repository->>DB: SELECT persisted import job or issue rows
        DB-->>Service: Stored results
        Service->>Service: Map existing job or issue DTOs
        opt Model or job absent or belongs to another ship
            Service-->>Controller: ResourceNotFoundException, HTTP 404
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
