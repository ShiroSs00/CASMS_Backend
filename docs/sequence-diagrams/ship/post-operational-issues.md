# POST /operational-issues

```mermaid
sequenceDiagram
    title POST /operational-issues — OperationalIssueController.report
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalIssueController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /operational-issues + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke report(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: issueService.report()

    Note over Controller,Service: Source branch: if cabin != null && cabin.getShip(
    Note over Controller,Service: Source branch: if cabin != null && cabin.getShip() != null
    Note over Controller,Service: Source branch: if cabinId == null
    Note over Controller,Service: Source branch: if cruise != null && cruise.getShip(
    Note over Controller,Service: Source branch: if cruise != null && cruise.getShip() != null
    Note over Controller,Service: Source branch: if cruiseId == null
    Note over Controller,Service: Source branch: if effectiveShipId == null
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
    Note over Controller,Service: Source branch: if request.getSeverity() == null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OperationalIssueRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
