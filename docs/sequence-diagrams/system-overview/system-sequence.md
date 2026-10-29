```mermaid
sequenceDiagram
    title CASMS HTTP request path (shared infrastructure)
    actor Client
    participant Web as Spring SecurityFilterChain
    participant JWT as JwtAuthenticationFilter
    participant Authz as Request authorization / method security
    participant Controller as Spring MVC Controller
    participant Service as Application service
    participant Repository as Spring Data repository
    participant DB as Database

    Client->>Web: HTTP request
    Web->>JWT: Filter request (stateless session; CSRF disabled)
    alt Invalid or absent authentication for protected route
        JWT-->>Client: Authentication failure (status handled by security entry point)
    else Request passes filter
        JWT->>Authz: Continue with authentication context
        Authz->>Authz: Apply SecurityConfig route matchers and method security
        alt Request or method permission denied
            Authz-->>Client: 401 / 403
        else Authorized
            Authz->>Controller: Dispatch matching handler
            Controller->>Service: Execute application operation
            Service->>Repository: Load or persist entities
            Repository->>DB: JPA query / write
            DB-->>Repository: Rows / write result
            Repository-->>Service: Entity / result
            Service-->>Controller: Result or propagated domain error
            Controller-->>Client: API response (status/body per handler and exception handling)
        end
    end
```

This is the common path derived from `SecurityConfig` and the Spring architecture. Public matchers, JWT behavior, controller branches and exception-to-status mappings are endpoint-specific; consult each handler and its diagram.
