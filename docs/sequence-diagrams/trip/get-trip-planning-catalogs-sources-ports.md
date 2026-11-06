# GET /trip-planning/catalogs/sources/ports

```mermaid
sequenceDiagram
    title GET /trip-planning/catalogs/sources/ports — TripCatalogController.getSourcePorts
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripCatalogController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /trip-planning/catalogs/sources/ports + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getSourcePorts(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected

    Note over Controller,Service: Source branch: for String dc : directCountries
    Note over Controller,Service: Source branch: if !allowedCountryCodes.contains(dc)
    Note over Controller,Service: Source branch: if !directCountries.isEmpty()
    Note over Controller,Service: Source branch: if CountryScopeValidator.isDomestic(trip)
    Note over Controller,Service: Source branch: if CountryScopeValidator.isInternational(trip)
    Note over Controller,Service: Source branch: if allowedCountryCodes != null
    Note over Controller,Service: Source branch: if countryCode != null && !countryCode.isBlank()
    Note over Controller,Service: Source branch: if countryCodes != null && !countryCodes.isEmpty()
    Note over Controller,Service: Source branch: if destinationId != null
    Note over Controller,Service: Source branch: if portRepository == null
    Note over Controller,Service: Source branch: if tripCountries == null || tripCountries.isEmpty()
    Note over Controller,Service: Source branch: if tripId != null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
