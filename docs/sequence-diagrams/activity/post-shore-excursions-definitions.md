# POST /shore-excursions/definitions

`ACTIVITY_SERVICE_MANAGER` with `EXCURSION_CREATE` creates a planned template. Input requires `name`, `supplierId`, `meetingPoint` and `destinationId`. HTTP 201 includes the stored `destinationId` in `data`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ShoreExcursionController
    participant Service as ShoreExcursionService
    participant Repo as JPA repositories
    participant DB as PostgreSQL
    Client->>Security: POST definition
    Security->>Controller: Authenticate and check role and authority
    Controller->>Service: Validated definition fields
    Note over Service,DB: Write transaction
    Service->>Repo: Load supplier and destination
    Repo->>DB: Read by IDs
    alt Missing or ineligible reference
        Service-->>Controller: 404 missing reference or business error
    else Active verified supplier and active destination
        Service->>Repo: Generate code and save planned definition with destination FK
        Repo->>DB: INSERT ShoreExcursion
        Service-->>Controller: Saved definition
        Controller-->>Client: 201 with destinationId
    end
```
