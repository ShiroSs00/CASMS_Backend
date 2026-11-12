# POST /staff/assignment-email-template/preview

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant API as StaffAssignmentEmailController
    participant Service as StaffAssignmentEmailService
    participant DB as PostgreSQL
    Client->>Auth: POST /staff/assignment-email-template/preview
    alt Unauthorized
        Auth-->>Client: 401 / 403
    else CRUISE_STAFF_ASSIGN
        Auth->>API: Invoke handler
        API->>Service: Validate fields and render Vietnamese example without writes
        Service-->>API: Result or mapped business error
        API-->>Client: 200 subject, text and HTML; 400/422 invalid fields
    end
```

See [email assignment flow](../ITINERARY-PROGRAM-FLOWS.md#email-phân-công-nhân-viên).
