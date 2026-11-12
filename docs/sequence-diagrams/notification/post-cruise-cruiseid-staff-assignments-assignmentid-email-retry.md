# POST /cruise/{cruiseId}/staff/assignments/{assignmentId}/email/retry

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant API as StaffAssignmentEmailController
    participant Service as StaffAssignmentEmailService
    participant DB as PostgreSQL
    Client->>Auth: POST /cruise/{cruiseId}/staff/assignments/{assignmentId}/email/retry
    alt Unauthorized
        Auth-->>Client: 401 / 403
    else CRUISE_STAFF_ASSIGN
        Auth->>API: Invoke handler
        API->>Service: Lock the scoped row and requeue only if delivery requires intervention
        Service->>DB: Execute bound JDBC query in transaction
        DB-->>Service: Query or persistence result
        Service-->>API: Result or mapped business error
        API-->>Client: 200 status; 404 unknown assignment email
    end
```

See [email assignment flow](../ITINERARY-PROGRAM-FLOWS.md#email-phân-công-nhân-viên).

## HR assignment lifecycle

An Admin account is outside the staff assignment email scope. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
