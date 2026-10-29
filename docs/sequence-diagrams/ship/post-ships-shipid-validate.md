# POST /ships/{shipId}/validate

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

For this change, only passenger-deck limit validation changes. Technical/service decks remain in ordinary listing and total physical counts. PATCH retains its existing checks and transactional field updates; validate is read-only. See ShipService for unrelated capacity and dimension checks.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipController
    participant Service as ShipService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /ships/{shipId}/validate
    Note over Security,Controller: Handler permission hasAuthority('SHIP_VALIDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: validateShip with bound parameters
        Controller->>Service: service.validateShip()
        Service->>Repository: Load Ship by ID
        Repository->>DB: SELECT Ship
        Service->>Repository: DeckRepository.countByShipShipIdAndServiceLevelFalse
        Repository->>DB: Count positive passenger decks only
        DB-->>Service: Passenger deck count
        Service->>Service: Compare configured totalDecks with passenger count
        opt Missing ship or business limit violation
            Service-->>Controller: HTTP 404 or business error HTTP 422
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
