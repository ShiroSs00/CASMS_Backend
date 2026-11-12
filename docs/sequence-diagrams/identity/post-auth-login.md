# POST /auth/login

```mermaid
sequenceDiagram
    actor Client
    participant Filter as Spring Security / JWT filter
    participant Controller as AuthController
    participant Service as AuthService
    participant Provider as Spring Security / BCrypt
    participant Repo as UserRepository / RolePermissionRepository
    participant DB as PostgreSQL

    Client->>Filter: POST /auth/login
    Filter->>Controller: Public route after shared bearer processing
    alt Malformed JSON or validation failure
        Controller-->>Client: 400 INVALID_REQUEST_BODY or VALIDATION_FAILED
    else Valid LoginRequest
        Controller->>Service: login(request)
        Note over Service,DB: Read-write transaction
        Service->>Provider: Authenticate username and password
        Provider->>Repo: Find by username
        Repo->>DB: Read user and roles
        alt Unknown user, wrong password or inactive account
            Service-->>Client: 401 INVALID_CREDENTIALS via exception advice
        else Authentication provider or persistence failure
            Service-->>Client: 500 INTERNAL_ERROR with traceId via exception advice
        else Active account authenticated
            Service->>Repo: Save lastLoginAt and read effective permissions
            Repo->>DB: Read permissions and persist lastLoginAt at flush
            Service->>Service: Sign JWT containing userId and username
            Service-->>Controller: LoginResponse after commit
            Controller-->>Client: 200 with access token, expiry, roles and permissions
        end
    end
```

Username is limited to 100 Unicode code points. Passwords are limited to 72 UTF-8 bytes. A persistence or commit failure is handled by auth-specific exception advice. The shared bearer and unexpected error handling is described in the [security flow](../README.md#shared-security-and-error-flow).
