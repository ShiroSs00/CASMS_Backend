# POST /decks/{deckId}/validate

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Uses ship.deckNumberLimit when present, with totalDecks fallback; a skipped passenger number does not imply too many decks.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckController
    participant Service as DeckService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /decks/{deckId}/validate
    Note over Security,Controller: Handler permission hasAuthority('DECK_VALIDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: validateDeck with bound parameters
        Controller->>Service: service.validateDeck()
        Note over Service,DB: Read-only transaction
        Service->>Repository: findById(deckId)
        Repository->>DB: SELECT deck and ship
        alt Deck missing
            Service-->>Controller: HTTP 404
        else Invalid number for level kind or above passenger deckNumberLimit
            Service-->>Controller: INVALID_DECK_NUMBER, HTTP 422
        else Valid positive passenger deck or non-positive technical level
            Service-->>Service: Validation succeeds
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
