# GET /cruise-plans/{cruiseId}/packages

```mermaid
sequenceDiagram
    title GET /cruise-plans/{cruiseId}/packages — CruiseScopedProgramsController.packages
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise-plans/{cruiseId}/packages + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke packages(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: plans.get()

    Note over Controller,Service: Source branch: for String table : List.of("Cabin", "Deck", "ShipRegulation")
    Note over Controller,Service: Source branch: for String table : List.of("CruiseStaff", "CruisePreparationSection", "CruiseRegulation")
    Note over Controller,Service: Source branch: for var row : activities) collectProgramIssues(issues, row, "ACTIVITY"
    Note over Controller,Service: Source branch: for var row : excursions) collectProgramIssues(issues, row, "EXCURSION"
    Note over Controller,Service: Source branch: if !"[]".equals(visits)) out.put("destinationVisits", json(visits)
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance)) b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(onboard)) b.add("ONBOARD_STAFF_REQUIRED"
    Note over Controller,Service: Source branch: if !approved(id, r)) b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !operational.isReady()
    Note over Controller,Service: Source branch: if !planIssues.isEmpty()) actions.remove("APPROVE"
    Note over Controller,Service: Source branch: if "APPROVED".equals(status(r)) && (!b.isEmpty() || pricingReturned(id))
    Note over Controller,Service: Source branch: if "SUBMITTED".equals(status(r))
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
