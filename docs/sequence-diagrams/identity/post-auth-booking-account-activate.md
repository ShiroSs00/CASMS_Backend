# POST /auth/booking-account/activate

```mermaid
sequenceDiagram
    actor Passenger
    participant Security as Spring Security
    participant Controller as BookingAccountController
    participant Service as BookingEmailService
    participant Repository as JdbcTemplate
    participant DB as PostgreSQL
    Passenger->>Security: Emailed token and new password
    Security->>Controller: Public auth route
    Controller->>Service: Validate request then activate
    Service->>Repository: Hash token, SELECT FOR UPDATE unexpired unused setup
    Repository->>DB: Begin transaction and lock setup row
    alt Invalid / expired / reused / disabled
        Service-->>Passenger: 400 or 403; no password change
    else Valid
        Service->>DB: Encoded password, consume setup token, redact email link
        DB-->>Passenger: Commit then 200
    end
```

See [reviewed flow and integration requirements](../CASMS-92-BOOKING-FLOWS.md).
