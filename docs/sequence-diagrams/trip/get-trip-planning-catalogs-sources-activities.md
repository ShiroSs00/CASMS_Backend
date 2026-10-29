# GET /trip-planning/catalogs/sources/activities

```mermaid
sequenceDiagram
    title GET /trip-planning/catalogs/sources/activities — TripCatalogController.getSourceActivities
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripCatalogController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /trip-planning/catalogs/sources/activities + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getSourceActivities(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected
        Controller->>Repository: OnboardActivityRepository.findAll()
    Note over Controller,Service: Source branch: if a.getCabin() != null
    Note over Controller,Service: Source branch: if a.getCategory() != null
    Note over Controller,Service: Source branch: if a.getDeck() != null
    Note over Controller,Service: Source branch: if a.getLocation() != null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
        Controller->>Repository: OnboardActivityRepository.findAll()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
