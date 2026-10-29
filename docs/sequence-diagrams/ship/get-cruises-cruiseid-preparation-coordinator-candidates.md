# GET /cruises/{cruiseId}/preparation/coordinator-candidates

```mermaid
sequenceDiagram
    title GET /cruises/{cruiseId}/preparation/coordinator-candidates — CruisePreparationController.coordinatorCandidates
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePreparationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruises/{cruiseId}/preparation/coordinator-candidates + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke coordinatorCandidates(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: preparationService.coordinatorCandidates()

    Note over Controller,Service: Source branch: if actor==null||principal==null||!actor.getUserId().equals(principal.getUserId()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
