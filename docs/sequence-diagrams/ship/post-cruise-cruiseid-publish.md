# POST /cruise/{cruiseId}/publish

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/publish — CruiseController.publish
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/publish + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke publish(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.publish()

    Note over Controller,Service: Source branch: for CruiseStaff cs : conflicts
    Note over Controller,Service: Source branch: for CruiseStaff s : activeStaff
    Note over Controller,Service: Source branch: if !blockers.isEmpty()
    Note over Controller,Service: Source branch: if !captainAssigned
    Note over Controller,Service: Source branch: if !conflicts.isEmpty()
    Note over Controller,Service: Source branch: if !itineraryApproved
    Note over Controller,Service: Source branch: if !itineraryConfigured
    Note over Controller,Service: Source branch: if !portsActive
    Note over Controller,Service: Source branch: if !readiness.isReady()
    Note over Controller,Service: Source branch: if !scheduleValid
    Note over Controller,Service: Source branch: if !shipActive
    Note over Controller,Service: Source branch: if !shipAssigned
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
