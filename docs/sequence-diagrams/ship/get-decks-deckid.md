# GET /decks/{deckId}

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
    Client->>Security: GET /decks/{deckId}
    Note over Security,Controller: Handler permission hasAuthority('DECK_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: detail with bound parameters
        Controller->>Service: service.detail()
        Note over Service,DB: Read-only transaction
        Service->>Repository: findById(deckId)
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
