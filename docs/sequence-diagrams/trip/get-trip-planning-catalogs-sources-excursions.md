# GET /trip-planning/catalogs/sources/excursions

```mermaid
sequenceDiagram
    title GET /trip-planning/catalogs/sources/excursions — TripCatalogController.getSourceExcursions
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripCatalogController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /trip-planning/catalogs/sources/excursions + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getSourceExcursions(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected
        Controller->>Repository: ShoreExcursionRepository.findAll()
    Note over Controller,Service: Source branch: if e.getCruiseStop() != null && e.getCruiseStop().getDestination() != null
    Note over Controller,Service: Source branch: if e.getCruiseStop() != null && e.getCruiseStop().getPort(
    Note over Controller,Service: Source branch: if e.getCruiseStop() != null && e.getCruiseStop().getPort() != null
    Note over Controller,Service: Source branch: if e.getPort() != null
    Note over Controller,Service: Source branch: if e.getPort() != null && e.getPort().getDestination() != null
    Note over Controller,Service: Source branch: if e.getSupplier() != null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
        Controller->>Repository: ShoreExcursionRepository.findAll()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
