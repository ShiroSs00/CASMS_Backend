# GET /employees/assignable-roles

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeAccountController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: GET /employees/assignable-roles
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_CREATE
        Security->>API: Invoke assignableRoles
        API->>Service: Resolve allowed roles
        Service-->>API: Result or mapped failure
        API-->>Client: 200 roles
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
