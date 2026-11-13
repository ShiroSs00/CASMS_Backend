# POST /cruises/{cruiseId}/delays/preview

OPERATIONS_MANAGER + CRUISE_START. See [departure and delay flows](../CRUISE-DEPARTURE-DELAY.md).

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant C as CruiseDelayController
    participant S as CruiseDelayService
    participant R as Repositories and existing services
    participant DB as PostgreSQL
    Client->>Auth: POST /cruises/{cruiseId}/delays/preview
    Auth->>C: Require OPERATIONS_MANAGER + CRUISE_START
    C->>S: Validate body and evaluate in read-only transaction
    S->>R: Read Cruise, schedule, readiness and constraints
    R->>DB: SELECT operational records and port windows
    S-->>C: Before, after, revision, countdown and blockers
    C-->>Client: 200 ApiResponse on success
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
