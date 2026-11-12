# POST /auth/employee-account/activate

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant API as EmployeeActivationController
    participant Service as Employee account / import service
    participant DB as PostgreSQL
    Client->>Security: POST /auth/employee-account/activate
    alt Rejected by security
        Security-->>Client: 401 / 403
    else Public with signed one-time token
        Security->>API: Invoke activate
        API->>Service: Validate signature and lock unconsumed row, update password and activate
        Service->>DB: Execute scoped bound queries in service transaction
        DB-->>Service: Result or constraint error
        Service-->>API: Result or mapped failure
        API-->>Client: 200; 400 invalid or expired token
    end
```

See the [reviewed HR flow](../ITINERARY-PROGRAM-FLOWS.md#tài-khoản-nhân-viên-và-nhập-excel).
