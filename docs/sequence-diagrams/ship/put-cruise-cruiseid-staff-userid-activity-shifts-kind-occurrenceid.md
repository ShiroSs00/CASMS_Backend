# PUT /cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant Controller as CruiseStaffWorkController
    participant Service as CruiseStaffWorkService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Client->>Auth: PUT /cruise/{cruiseId}/staff/{userId}/activity-shifts/{kind}/{occurrenceId}
    alt Unauthorized
        Auth-->>Client: 401 or 403
    else CRUISE_STAFF_ASSIGN
        Auth->>Controller: Authorized request
        Controller->>Service: assignActivity in transaction
        Service->>Repository: Validate scoped occurrence, qualification and schedule before inserting duty
        Repository->>DB: Execute bound SQL
        DB-->>Service: Result or constraint violation
        Service-->>Controller: Return or map business error
        Controller-->>Client: 204; 404 occurrence; 409 overlap or missing qualification
    end
```

See the [shared work scheduling flow](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).

## HR assignment lifecycle

The target user lock excludes Admin accounts. Existing service, qualification and overlap rules continue to apply. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
