# GET /staff/{userId}/activity-qualifications

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant Controller as CruiseStaffWorkController
    participant Service as CruiseStaffWorkService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Client->>Auth: GET /staff/{userId}/activity-qualifications
    alt Unauthorized
        Auth-->>Client: 401 or 403
    else CRUISE_STAFF_ASSIGN
        Auth->>Controller: Authorized request
        Controller->>Service: qualifications in transaction
        Service->>Repository: Read active qualifications by employee
        Repository->>DB: Execute bound SQL
        DB-->>Service: Result or constraint violation
        Service-->>Controller: Return or map business error
        Controller-->>Client: 200 qualifications
    end
```

See the [shared work scheduling flow](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).

## HR assignment lifecycle

Admin account qualification records are excluded from results. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
