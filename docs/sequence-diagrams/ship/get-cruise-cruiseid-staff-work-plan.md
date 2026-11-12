# GET /cruise/{cruiseId}/staff/work-plan

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant Controller as CruiseStaffWorkController
    participant Service as CruiseStaffWorkService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Client->>Auth: GET /cruise/{cruiseId}/staff/work-plan
    alt Unauthorized
        Auth-->>Client: 401 or 403
    else CRUISE_STAFF_ASSIGN or CRUISE_STAFF_READ
        Auth->>Controller: Authorized request
        Controller->>Service: workPlan in transaction
        Service->>Repository: Read sorted dated windows, assigned staff and services
        Repository->>DB: Execute bound SQL
        DB-->>Service: Result or constraint violation
        Service-->>Controller: Return or map business error
        Controller-->>Client: 200 WorkPlan; 404 cruise
    end
```

See the [shared work scheduling flow](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).
