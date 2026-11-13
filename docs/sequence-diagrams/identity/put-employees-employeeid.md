# PUT /employees/{employeeId}

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeAccountController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: PUT /employees/{employeeId}
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_UPDATE
        Security->>API: Invoke update
        API->>Service: Lock profile and account, apply changes and audit; rotate pending token on email change
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 200 profile; 404 unknown or out of scope; 409 conflict
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
