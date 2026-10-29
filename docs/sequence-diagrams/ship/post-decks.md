# POST /decks

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Manual deck creation remains restricted to passenger decks. Technical deck numbers come from model import; the response includes serviceLevel and nullable modelMetadataJson.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckController
    participant Service as DeckService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /decks
    Note over Security,Controller: Handler permission hasAuthority('DECK_CREATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: create with bound parameters
        Controller->>Service: service.create()
        Note over Controller: Validate positive optional number and priceMultiplier constraints
        Note over Service,DB: Transaction begins
        Service->>Service: Reject caller-provided model transforms
        Service->>Repository: Load Ship and existing deck numbers
        Repository->>DB: SELECT Ship and Decks
        Service->>Service: Choose unused positive passenger number within ship limit
        Service->>Repository: Save passenger Deck with serviceLevel false
        Repository->>DB: INSERT Deck with unique ship-number constraint and commit
        opt Missing ship, invalid number, duplicate or model field mutation
            Service-->>Controller: HTTP 404 or business error HTTP 422, rollback
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 201 ApiResponse of DeckResponse
    end
```
