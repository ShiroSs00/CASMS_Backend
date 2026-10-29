# PUT /cruises/{cruiseId}/preparation/coordinator

```mermaid
sequenceDiagram
    title PUT /cruises/{cruiseId}/preparation/coordinator — CruisePreparationController.assignCoordinator
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePreparationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /cruises/{cruiseId}/preparation/coordinator + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignCoordinator(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: preparationService.assignCoordinator()

    Note over Controller,Service: Source branch: if !coordinator.hasRole(com.casms.identity.enums.Role.CRUISE_MANAGER)||coordinator.getStatus(
    Note over Controller,Service: Source branch: if actor==null||!Objects.equals(actorId,actor.getUserId())||!actor.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER
    Note over Controller,Service: Source branch: if cruise.getPreparationCoordinator()!=null&&coordinatorId.equals(cruise.getPreparationCoordinator().getUserId())
    Note over Controller,Service: Source branch: if expectedRevision==null||!expectedRevision.equals(cruise.getRevision())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.saveAndFlush()
            Service->>Repository: com.casms.identity.repository.UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
