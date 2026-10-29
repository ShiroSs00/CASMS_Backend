# GET /operational-issues

```mermaid
sequenceDiagram
    title GET /operational-issues — OperationalIssueController.list
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalIssueController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /operational-issues + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke list(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: issueService.list()

    Note over Controller,Service: Source branch: if cruiseId != null
    Note over Controller,Service: Source branch: if shipId != null
    Note over Controller,Service: Source branch: if status != null
    Note over Controller,Service: Source branch: if status != null && cruiseId != null
    Note over Controller,Service: Source branch: if status != null && shipId != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OperationalIssueRepository.findAllByOrderByReportedAtDesc()
            Service->>Repository: OperationalIssueRepository.findByCruise_CruiseIdOrderByReportedAtDesc()
            Service->>Repository: OperationalIssueRepository.findByShip_ShipIdOrderByReportedAtDesc()
            Service->>Repository: OperationalIssueRepository.findByStatusAndCruise_CruiseIdOrderByReportedAtDesc()
            Service->>Repository: OperationalIssueRepository.findByStatusAndShip_ShipIdOrderByReportedAtDesc()
            Service->>Repository: OperationalIssueRepository.findByStatusOrderByReportedAtDesc()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
