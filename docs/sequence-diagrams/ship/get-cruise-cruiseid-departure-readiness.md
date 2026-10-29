# GET /cruise/{cruiseId}/departure-readiness

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/departure-readiness — CruiseController.getDepartureReadiness
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/departure-readiness + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getDepartureReadiness(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.validateDepartureReadiness()

    Note over Controller,Service: Source branch: for CruisePreparationSectionResponse sec : prep.getSections()
    Note over Controller,Service: Source branch: for CruiseStaff cs : conflicts
    Note over Controller,Service: Source branch: for CruiseStaff s : activeStaff
    Note over Controller,Service: Source branch: if !"CONFIRMED".equalsIgnoreCase(sec.getStatus())
    Note over Controller,Service: Source branch: if !blockers.isEmpty()
    Note over Controller,Service: Source branch: if !captainAssigned
    Note over Controller,Service: Source branch: if !conflicts.isEmpty()
    Note over Controller,Service: Source branch: if !itineraryApproved
    Note over Controller,Service: Source branch: if !itineraryConfigured
    Note over Controller,Service: Source branch: if !portsActive
    Note over Controller,Service: Source branch: if !prep.isAllConfirmed()
    Note over Controller,Service: Source branch: if !scheduleValid
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
