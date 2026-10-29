# DELETE /decks/{deckId}/unused

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Unassign services before deleting an unused deck. No cascade removes catalogue services or assignments.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckController
    participant Service as DeckService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: DELETE /decks/{deckId}/unused
    Note over Security,Controller: Handler permission hasAuthority('DECK_DELETE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: deleteUnused with bound parameters
        Controller->>Service: service.deleteUnused()
        Note over Service,DB: Transaction begins
        Service->>Repository: Load Deck, check Cabin, ShipModelObject and LocationDevice references
        Repository->>DB: SELECT deck and reference existence
        alt Deck missing or known references exist
            Service-->>Controller: HTTP 404 or DECK_IN_USE HTTP 422
        else No known references
            Service->>Repository: delete Deck
            Repository->>DB: DELETE subject to foreign keys
            alt Manual service assignment still references deck
                DB-->>Service: Foreign key violation, rollback
                Service-->>Controller: DATA_INTEGRITY_VIOLATION HTTP 409
            else No references
                Note over Service,DB: Commit delete
            end
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 204 No Content
    end
```
