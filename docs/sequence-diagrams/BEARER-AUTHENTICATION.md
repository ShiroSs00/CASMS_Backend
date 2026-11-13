# Shared security and error flow

This flow applies to the mappings in the [API inventory](API-INVENTORY.md).

The JWT filter runs before controller dispatch, including on public routes. `POST /auth/refresh` validates its own token. Per-route authorization and CSRF rules still apply.

```mermaid
sequenceDiagram
    actor Client
    participant Filter as JwtAuthenticationFilter
    participant JWT as JwtService
    participant Repo as UserRepository / RolePermissionRepository
    participant DB as PostgreSQL
    participant Security as Spring Security
    participant MVC as Controller / exception advice

    Client->>Filter: Request
    opt Bearer header outside POST /auth/refresh, no existing principal
        Filter->>JWT: Verify signed JWT and obtain userId once
        opt Valid userId claim
            Filter->>Repo: Find user by ID
            Repo->>DB: Read user and roles
            opt Active account
                Filter->>Repo: Read current role permissions
                Repo->>DB: Read permissions
                Filter->>Filter: Set authenticated principal
            end
        end
    end
    alt Filter lookup or authentication infrastructure failure
        Filter->>MVC: Resolve AuthenticationServiceException
        MVC-->>Client: 500 INTERNAL_ERROR with traceId
    else Filter completes
        Filter->>Security: Continue request
        alt Protected request with unusable Bearer authentication
            Security-->>Client: 401 with Bearer challenge
        else Route allowed
            Security->>MVC: Dispatch controller
            opt Unhandled exception
                MVC-->>Client: 500 INTERNAL_ERROR with generic message and traceId
            end
        end
    end
```

An unusable bearer token leaves the request anonymous. Public controllers can still handle the request. Protected routes keep the existing behavior for requests without a Bearer header, CSRF failures, and authenticated callers with insufficient permissions. Malformed JSON returns the existing `400 INVALID_REQUEST_BODY` envelope and the parser message is omitted from the log.
