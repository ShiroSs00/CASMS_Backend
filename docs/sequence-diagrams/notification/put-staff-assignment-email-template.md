# PUT /staff/assignment-email-template

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Spring Security
    participant API as StaffAssignmentEmailController
    participant Service as StaffAssignmentEmailService
    participant DB as PostgreSQL
    Client->>Auth: PUT /staff/assignment-email-template
    alt Unauthorized
        Auth-->>Client: 401 / 403
    else CRUISE_STAFF_ASSIGN
        Auth->>API: Invoke handler
        API->>Service: Validate fields and insert a new version with actor and time
        Service->>DB: Execute bound JDBC query in transaction
        DB-->>Service: Query or persistence result
        Service-->>API: Result or mapped business error
        API-->>Client: 200 PublishedTemplate; 400/422 invalid fields
    end
```

See [email assignment flow](../ITINERARY-PROGRAM-FLOWS.md#email-phân-công-nhân-viên).
