# GET /ships/{shipId}/3d-configuration

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Service level codes retain the SERVICE_number convention. No services are inferred from model areas. Deck-service assignments are queried through the dedicated deck API.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipModelController
    participant Service as ShipModelService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: GET /ships/{shipId}/3d-configuration
    Note over Security,Controller: Handler permission hasAuthority('SHIP_CONFIGURATION_READ')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: configuration with bound parameters
        Controller->>Service: service.configuration()
        Note over Service,DB: Read-only transaction
        Service->>Repository: Load Ship, latest READY model, model objects, decks, cabins and BLE devices
        Repository->>DB: SELECT configuration inputs
        DB-->>Service: Domain rows
        Service->>Service: Select DECK mappings only for deck transforms
        Service->>Service: Map all physical deck UUIDs, serviceLevel flags and rooms or anchors
        Note over Service: Legacy model-only levels are retained only if no physical deck with that number exists
        opt Validation endpoint
            Service->>Service: Check passenger number range, room mappings, vectors and BLE positions
        end
        opt Ship absent, model not READY or invalid configuration
            Service-->>Controller: HTTP 404 or business error HTTP 422
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
