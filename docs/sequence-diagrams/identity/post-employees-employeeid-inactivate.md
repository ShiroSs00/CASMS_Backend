# POST /employees/{employeeId}/inactivate

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeAccountController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /employees/{employeeId}/inactivate
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Admin/HR + USER_STATUS_CHANGE
        Security->>API: Invoke inactivate
        API->>Service: Lock employee and assigned cruises, reject active cruise, end future duties and disable account
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 200 profile; 409 active cruise
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).

## HR assignment lifecycle

Locks relevant cruises before locking Employee and User. Reuses the normal removal transition, audit and replacement notification. Conflicting lifecycle or concurrent assignment causes rollback. See [assignment lifecycle](../ITINERARY-PROGRAM-FLOWS.md#một-chuyến-đang-hiệu-lực-cho-mỗi-nhân-viên).
