# POST /cabins

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

The new technical deck rows must not relax the existing invariant that passenger cabins belong to positive passenger decks.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as CabinController
    participant Service as CabinService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /cabins
    Note over Security,Controller: Handler permission hasAuthority('CABIN_CREATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: create with bound parameters
        Controller->>Service: service.create()
        Note over Controller: Bean validation of cabin request
        Note over Service,DB: Transaction begins
        Service->>Repository: DeckRepository.findById
        Repository->>DB: SELECT deck
        alt Deck absent
            Service-->>Controller: HTTP 404
        else Deck is a technical service level
            Service-->>Controller: CABIN_PASSENGER_DECK_REQUIRED, HTTP 422, rollback
        else Passenger deck
            Service->>Service: Existing ship, cabin identity, capacity and immutable-model checks
            Service->>Repository: Save cabin if all existing validations pass
            Repository->>DB: INSERT Cabin and commit
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 201 ApiResponse of CabinSummaryResponse
    end
```
