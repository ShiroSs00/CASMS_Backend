# GET /decks/{deckId}/services

CASMS-90 reviewed against implementation on 2026-10-29. Paths are relative to `/api`; optional `shipId` scopes the physical deck.

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
        Note over Controller: Invalid deckId or shipId UUID returns 400 before service call
        Controller->>Service: list(deckId, shipId)
        Note over Service,DB: Read-only transaction
        Service->>Repository: DeckRepository.findById
        Repository->>DB: SELECT deck and its owning ship
        opt Explicit shipId supplied
            Service->>Repository: ShipRepository.existsById(shipId)
            Repository->>DB: SELECT ship existence
            Service->>Service: Compare deck.ship.shipId with requested shipId
        end
        alt Deck absent, requested ship absent or deck outside requested ship
            Service-->>Controller: ResourceNotFoundException, HTTP 404
        else Valid deck and optional ship scope
            Service->>Repository: findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc
            Repository->>DB: SELECT assignments with deck, ship and service entity graph
            DB-->>Service: Rows or empty list
            Service->>Service: Map service identity, type, status and assignment timestamp
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
