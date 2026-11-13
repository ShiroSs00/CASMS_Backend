# POST /cruises/{cruiseId}/delays

OPERATIONS_MANAGER + CRUISE_START. See [departure and delay flows](../CRUISE-DEPARTURE-DELAY.md).

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant C as CruiseDelayController
    participant S as CruiseDelayService
    participant R as Repositories and existing services
    participant DB as PostgreSQL
    Client->>Auth: POST /cruises/{cruiseId}/delays
    Auth->>C: Require OPERATIONS_MANAGER + CRUISE_START
    C->>S: Validate body, pass actor and begin write transaction
    S->>R: Lock cruise, check audit identity and revision
    R->>DB: SELECT FOR UPDATE and read audit event
    alt Matching retry
        S-->>C: Return existing event
    else Fresh request
        S->>R: Lock assigned ship and stops and recheck current readiness and port constraints
        alt Blocked or stale
            S-->>C: 422 / 409 and rollback
        else Eligible
            R->>DB: Shift schedule, persist snapshots and internal notifications
            S-->>C: Confirmed audit event after commit
        end
    end
    C-->>Client: 200 ApiResponse on success
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
