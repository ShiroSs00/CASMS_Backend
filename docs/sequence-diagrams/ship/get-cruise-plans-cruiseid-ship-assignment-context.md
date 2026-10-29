# GET /cruise-plans/{cruiseId}/ship-assignment-context

```mermaid
sequenceDiagram
    title GET /cruise-plans/{cruiseId}/ship-assignment-context — CruiseMainflowController.assignmentContext
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseMainflowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-plans/{cruiseId}/ship-assignment-context + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignmentContext(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.assignmentContext()

    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(captain))b.add("CAPTAIN_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance))b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !approved(id,r))b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !operational.isReady()) b.addAll(operational.getBlockers()
    Note over Controller,Service: Source branch: if "SUBMITTED".equals(status(r)))actions.addAll(List.of("WITHDRAW","APPROVE","REJECT","REQUEST_REVISION")
    Note over Controller,Service: Source branch: if Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(status(r)))actions.add("SUBMIT"
    Note over Controller,Service: Source branch: if approved(id,r)
    Note over Controller,Service: Source branch: if b.isEmpty())actions.add("SET_READY"
    Note over Controller,Service: Source branch: if c.get("booking_opens_at")==null||c.get("booking_closes_at")==null)b.add("BOOKING_WINDOW_REQUIRED"
    Note over Controller,Service: Source branch: if confirmed==null||confirmed!=5)b.add("PREPARATION_CONFIRMATION_REQUIRED"
    Note over Controller,Service: Source branch: if found.isEmpty()) throw new ResourceNotFoundException("Cruise", id
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
