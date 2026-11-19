# PATCH /shore-excursions/definitions/{id}/destination

`ACTIVITY_SERVICE_MANAGER` with `EXCURSION_UPDATE` provides a required `destinationId` to complete a legacy planned template. Success returns the updated definition. Repeating the same assignment is safe. A different existing destination causes a conflict.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ShoreExcursionController
    participant Service as ShoreExcursionService
    participant Repo as JPA repositories
    participant DB as PostgreSQL
    Client->>Security: PATCH template destination
    Security->>Controller: Authenticate and check role and authority
    Controller->>Service: Template ID and validated destination ID
    Note over Service,DB: Write transaction
    Service->>Repo: Lock template and look up active destination
    Repo->>DB: SELECT with row lock and destination lookup
    alt Template missing, unavailable or destination invalid
        Service-->>Controller: Error without updating the template
    else Template can be completed
        Service->>Repo: Store destination FK
        Repo->>DB: UPDATE ShoreExcursion at commit
        Controller-->>Client: 200 with destinationId
    end
```
