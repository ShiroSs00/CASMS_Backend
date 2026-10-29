# GET /ships/{shipId}/models/{modelId}

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Model detail now includes serviceDecks as linked deck rows after reimport. Existing manifest and model scope validation still applies.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipModelController
    participant Service as ShipModelAssetService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: GET /ships/{shipId}/models/{modelId}
    Note over Security,Controller: Handler permission hasAuthority('SHIP_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: detail with bound parameters
        Controller->>Service: assetService.detail()
        Note over Service,DB: Read-only transaction
        Service->>Repository: Resolve ship-scoped model and load ShipModelObjects
        Repository->>DB: SELECT model and persisted mappings
        DB-->>Service: Rows including technical decks with real deck references
        Service->>Service: Build existing response, count mapped decks or map node keys to deckIds
        opt Model absent or outside ship scope
            Service-->>Controller: HTTP 404
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
