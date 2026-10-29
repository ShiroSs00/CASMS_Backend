# POST /auth/logout

```mermaid
sequenceDiagram
    title POST /auth/logout — AuthController.logout
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as AuthController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /auth/logout + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke logout(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
