# POST /employees/imports

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeImportController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /employees/imports
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_CREATE
        Security->>API: Invoke upload
        API->>Service: Validate workbook and persist batch, row errors and audit
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 201 preview; 400 malformed workbook
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
