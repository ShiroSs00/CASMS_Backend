# POST /ports

```mermaid
sequenceDiagram
    title POST /ports — PortController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PortController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ports + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if !PortTimeZoneHelper.isValidTimeZoneId(timeZoneId)
    Note over Controller,Service: Source branch: if port.getDestination() != null
    Note over Controller,Service: Source branch: if portRepository.existsByPortCode(request.getPortCode())
    Note over Controller,Service: Source branch: if rawCountryCode == null && destination != null
    Note over Controller,Service: Source branch: if request.getDestinationId() != null
    Note over Controller,Service: Source branch: if timeZoneId == null || timeZoneId.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: DestinationRepository.findById()
            Service->>Repository: PortRepository.existsByPortCode()
            Service->>Repository: PortRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
