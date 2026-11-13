# POST /auth/logout

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security / JWT filter
    participant Controller as AuthController

    Client->>Security: POST /auth/logout
    Security->>Controller: Public route after shared bearer processing
    Controller-->>Client: 200 Logged out successfully
    Client->>Client: Discard local token
```

The controller has no transaction or token store. A valid token can still be used until expiration while the account remains active. Bearer authentication failures follow the [shared security flow](../README.md#shared-security-and-error-flow).
