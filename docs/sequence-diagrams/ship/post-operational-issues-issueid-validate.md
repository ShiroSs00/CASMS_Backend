# POST /operational-issues/{issueId}/validate

```mermaid
sequenceDiagram
    title POST /operational-issues/{issueId}/validate — OperationalIssueController.validate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalIssueController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /operational-issues/{issueId}/validate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: issueService.validateIssue()

    Note over Controller,Service: Source branch: if cabin != null && cabin.getShip(
    Note over Controller,Service: Source branch: if cruise != null && cruise.getShip(
    Note over Controller,Service: Source branch: if issue.getDescription() == null || issue.getDescription().isBlank()
    Note over Controller,Service: Source branch: if issue.getIssueCode() == null || issue.getIssueCode().isBlank()
    Note over Controller,Service: Source branch: if issue.getResolution(
    Note over Controller,Service: Source branch: if issue.getStatus(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
