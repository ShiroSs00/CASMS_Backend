# GET /employees/imports/template

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeImportController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: GET /employees/imports/template
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_CREATE
        Security->>API: Invoke template
        API->>Service: Build XLSX with headers and permitted roles
        Service-->>API: Result or mapped failure
        API-->>Client: 200 XLSX template
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
