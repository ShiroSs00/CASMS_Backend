# POST /cruise-plans/{cruiseId}/{action:submit|withdraw}

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/{action:submit|withdraw} — CruiseMainflowController.submit
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseMainflowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/{action:submit|withdraw} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submit(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.transition()

    Note over Controller,Service: Source branch: for String table:List.of("TourPackageService","TourPackageActivity","TourPackageExcursion")
    Note over Controller,Service: Source branch: if !"APPROVE".equals(action)&&(request.reason()==null||request.reason().isBlank())
    Note over Controller,Service: Source branch: if !"SUBMITTED".equals(before))throw conflict(
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(captain))b.add("CAPTAIN_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance))b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !Objects.equals(r.get("fingerprint"),hash(snapshot))
    Note over Controller,Service: Source branch: if !Set.of("DOMESTIC","INTERNATIONAL").contains(Objects.toString(c.get("planned_scope"),"")))b.add("SCOPE_COUNTRY_REQUIRED"
    Note over Controller,Service: Source branch: if !Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(before))throw conflict(
    Note over Controller,Service: Source branch: if !approved(id,r))b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !blockers.isEmpty()
    Note over Controller,Service: Source branch: if !operational.isReady()) b.addAll(operational.getBlockers()
    Note over Controller,Service: Source branch: if "SUBMITTED".equals(status(r)))actions.addAll(List.of("WITHDRAW","APPROVE","REJECT","REQUEST_REVISION")
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
