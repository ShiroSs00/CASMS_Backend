# POST /employees/{employeeId}/activation-email/retry

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeAccountController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /employees/{employeeId}/activation-email/retry
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_UPDATE
        Security->>API: Invoke retryActivationEmail
        API->>Service: Requeue failed or expired unsent email only
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 204; 404 unknown or out of scope
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
