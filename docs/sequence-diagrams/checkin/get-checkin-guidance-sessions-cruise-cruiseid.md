# GET /checkin/guidance-sessions/cruise/{cruiseId}

```mermaid
sequenceDiagram
    title GET /checkin/guidance-sessions/cruise/{cruiseId} — GuidanceSessionController.listByCruise
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as GuidanceSessionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /checkin/guidance-sessions/cruise/{cruiseId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke listByCruise(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: guidanceSessionService.listByCruise()

    Note over Controller,Service: Source branch: if !cruiseRepository.existsById(cruiseId)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.existsById()
            Service->>Repository: GuidanceSessionRepository.findByCruise_CruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
