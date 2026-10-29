# GET /ports

```mermaid
sequenceDiagram
    title GET /ports — PortController.list
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PortController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ports + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke list(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.list()

    Note over Controller,Service: Source branch: if countryCode != null && !countryCode.isBlank()) combinedCountries.add(countryCode.trim().toUpperCase()
    Note over Controller,Service: Source branch: if countryCodes != null) combinedCountries.addAll(countryCodes.stream().filter(c -> c != null && !c.isBlank()).map(c -> c.trim(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
