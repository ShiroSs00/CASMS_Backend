# Bearer authentication

This flow precedes route authorization for the mappings in the [API inventory](API-INVENTORY.md).

```mermaid
sequenceDiagram
    actor Client
    participant Filter as JwtAuthenticationFilter
    participant Users as AuthenticationService
    participant Repository as UserRepository
    participant DB as PostgreSQL
    participant Security as Spring Security
    participant API as Route controller
    Client->>Filter: Request with optional bearer token
    opt Valid token with username and no existing authentication
        Filter->>Users: Load current user by username
        Users->>Repository: findByUsername(username)
        Repository->>DB: Query user
        DB-->>Repository: User and current account status
        Repository-->>Users: User
        Users-->>Filter: CasmsPrincipal
        alt Enabled and unlocked
            Filter->>Filter: Set authentication with current authorities
        else Disabled or locked
            Filter->>Filter: Leave authentication unset
        end
    end
    Filter->>Security: Continue filter chain
    alt Request meets route security rules
        Security->>API: Dispatch using route policy
    else Request denied
        Security-->>Client: Access denied (403 for employee import)
    end
```

The filter does not write account state. Public routes retain their own security and application rules.
