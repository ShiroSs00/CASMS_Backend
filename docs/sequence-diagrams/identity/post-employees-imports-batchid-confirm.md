# POST /employees/imports/{batchId}/confirm

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeImportController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /employees/imports/{batchId}/confirm
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_CREATE
        Security->>API: Invoke confirm
        API->>Service: Revalidate pending rows under a per-row lock and independent transactions
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 200 result per row; 404 out of scope
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
