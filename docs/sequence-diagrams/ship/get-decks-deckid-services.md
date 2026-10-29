# GET /decks/{deckId}/services

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

GET `/decks/**` passes the route matcher, but both DECK_READ and SERVICE_READ are still mandatory at method security.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckServiceAssignmentController
    participant Service as DeckServiceAssignmentService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: GET /decks/{deckId}/services
    Note over Security,Controller: Handler permission hasAuthority('DECK_READ') and hasAuthority('SERVICE_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: list with bound parameters
        Controller->>Service: service.list()
        Note over Service,DB: Read-only transaction
        Service->>Repository: DeckRepository.existsById
        Repository->>DB: SELECT deck existence
        alt Deck absent
            Service-->>Controller: ResourceNotFoundException, HTTP 404
        else Deck exists
            Service->>Repository: findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc
            Repository->>DB: SELECT assignments with deck, ship and service entity graph
            DB-->>Service: Rows or empty list
            Service->>Service: Map service identity, type, status and assignment timestamp
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
