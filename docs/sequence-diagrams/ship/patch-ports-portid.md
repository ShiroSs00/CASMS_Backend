# PATCH /ports/{portId}

```mermaid
sequenceDiagram
    title PATCH /ports/{portId} — PortController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PortController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /ports/{portId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: if !PortTimeZoneHelper.isValidTimeZoneId(request.getTimeZoneId())
    Note over Controller,Service: Source branch: if port.getDestination() != null
    Note over Controller,Service: Source branch: if request.getDescription() != null
    Note over Controller,Service: Source branch: if request.getDestinationId() != null
    Note over Controller,Service: Source branch: if request.getLatitude() != null
    Note over Controller,Service: Source branch: if request.getLocationType() != null
    Note over Controller,Service: Source branch: if request.getLongitude() != null
    Note over Controller,Service: Source branch: if request.getPortName() != null
    Note over Controller,Service: Source branch: if request.getTimeZoneId() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: DestinationRepository.findById()
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
