# GET /decks/by-ship/{shipId}

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

All physical levels have deckId UUIDs. modelMetadataJson preserves source deck fields; it does not create services.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckController
    participant Service as DeckService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: GET /decks/by-ship/{shipId}
    Note over Security,Controller: Handler permission hasAuthority('DECK_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: listByShip with bound parameters
        Controller->>Service: service.listByShip()
        Note over Service,DB: Read-only transaction
        Service->>Repository: check Ship existence then findByShipShipId(shipId, pageable)
        Repository->>DB: SELECT decks, including service_level rows
        DB-->>Service: Deck rows or page
        Service->>Service: Map DeckResponse, including serviceLevel and modelMetadataJson
        opt Required ship or deck missing
            Service-->>Controller: ResourceNotFoundException, HTTP 404
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
