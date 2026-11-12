# PUT /staff/{userId}/activity-qualifications/{kind}/{definitionId}

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant Controller as CruiseStaffWorkController
    participant Service as CruiseStaffWorkService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Client->>Auth: PUT /staff/{userId}/activity-qualifications/{kind}/{definitionId}
    alt Unauthorized
        Auth-->>Client: 401 or 403
    else CRUISE_STAFF_ASSIGN
        Auth->>Controller: Authorized request
        Controller->>Service: addQualification in transaction
        Service->>Repository: Lock active onboard employee, validate source and insert qualification once
        Repository->>DB: Execute bound SQL
        DB-->>Service: Result or constraint violation
        Service-->>Controller: Return or map business error
        Controller-->>Client: 204; 404 invalid source; 409 inactive or wrong role
    end
```

See the [shared work scheduling flow](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).

## HR assignment lifecycle

The target user lock excludes Admin accounts. Existing service, qualification and overlap rules continue to apply. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
