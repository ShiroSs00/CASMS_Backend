# POST /cruise-plans/{cruiseId}/ready

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/ready — CruiseMainflowController.ready
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseMainflowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/ready + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke ready(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.setReady()

    Note over Controller,Service: Source branch: for String table:List.of("TourPackageService","TourPackageActivity","TourPackageExcursion")
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(captain))b.add("CAPTAIN_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance))b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !Objects.equals(request.fingerprint(),evaluated.fingerprint())
    Note over Controller,Service: Source branch: if !approved(id,r))b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !evaluated.readyBlockers().isEmpty()
    Note over Controller,Service: Source branch: if !operational.isReady()) b.addAll(operational.getBlockers()
    Note over Controller,Service: Source branch: if "SUBMITTED".equals(status(r)))actions.addAll(List.of("WITHDRAW","APPROVE","REJECT","REQUEST_REVISION")
    Note over Controller,Service: Source branch: if Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(status(r)))actions.add("SUBMIT"
    Note over Controller,Service: Source branch: if actor==null)throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Authentication required"
    Note over Controller,Service: Source branch: if approved(id,r)
    Note over Controller,Service: Source branch: if auth==null||!(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
