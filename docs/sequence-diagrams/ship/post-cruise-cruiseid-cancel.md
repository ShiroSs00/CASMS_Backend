# POST /cruise/{cruiseId}/cancel

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method security
    participant Controller as CruiseController
    participant Service as CruiseService
    participant Repository as Repositories
    participant DB as PostgreSQL
    Client->>Security: POST /cruise/{cruiseId}/cancel
    alt Authentication or authorization rejected
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Require CRUISE_CANCEL
        Controller->>Service: Cancel transaction
        Service->>Repository: Lock cruise before checking lifecycle
        Repository->>DB: SELECT FOR UPDATE
        alt IN_PROGRESS or COMPLETED
            Service-->>Client: 409 and roll back
        else Already cancelled
            Service-->>Client: 200 current cruise
        else Missing reason or active bookings
            Service-->>Client: 400 or 409
        else Cancellation allowed
            Service->>DB: Save cruise cancellation and audit
            Note over Service,DB: Ship status unchanged
            Service-->>Client: 200 with CANCELLED after commit
        end
    end
```

The cruise row lock prevents a cancellation from overwriting a concurrent start. After start commits, this endpoint rejects the status as IN_PROGRESS. Cancellation leaves the ship's status unchanged.

## HR assignment lifecycle

When Cruise becomes CANCELLED, V100 closes ACTIVE staff rows as CANCELLED in the same transaction. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
