# GET /trip-planning/catalogs/sources/services

```mermaid
sequenceDiagram
    title GET /trip-planning/catalogs/sources/services — TripCatalogController.getSourceServices
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripCatalogController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /trip-planning/catalogs/sources/services + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getSourceServices(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected
        Controller->>Repository: ServiceRepository.findAll()

        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
        Controller->>Repository: ServiceRepository.findAll()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
