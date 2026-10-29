# PATCH /decks/{deckId}

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Non-positive technical numbering is set by model import. This PATCH does not convert a technical deck into a passenger deck or modify its model geometry.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckController
    participant Service as DeckService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: PATCH /decks/{deckId}
    Note over Security,Controller: Handler permission hasAuthority('DECK_UPDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: update with bound parameters
        Controller->>Service: service.update()
        Note over Controller: Validate DeckUpdateRequest, including positive explicit deckNumber
        Note over Service,DB: Transaction begins
        Service->>Repository: findById(deckId)
        Repository->>DB: SELECT deck and ship
        alt Deck missing
            Service-->>Controller: HTTP 404
        else Attempt to change model transforms or renumber service level
            Service-->>Controller: DECK_MODEL_FIELDS_IMMUTABLE, HTTP 422, rollback
        else Business update allowed
            Service->>Service: Check passenger number range and uniqueness when changed
            Service->>Repository: Save permitted name or priceMultiplier changes
            Repository->>DB: UPDATE Deck, commit
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
