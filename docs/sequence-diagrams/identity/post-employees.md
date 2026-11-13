# POST /employees

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeAccountController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /employees
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_CREATE
        Security->>API: Invoke create
        API->>Service: Validate role and write User, Employee, activation outbox and audit together
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 201 Employee; 403 role; 409 conflict
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
