# POST /cruises/{cruiseId}/preparation/{sectionCode}/confirm

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/preparation/{sectionCode}/confirm — CruisePreparationController.confirmSection
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePreparationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/preparation/{sectionCode}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirmSection(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: preparationService.confirmSection()

    Note over Controller,Service: Source branch: if !"SUBMITTED".equalsIgnoreCase(section.getStatus())
    Note over Controller,Service: Source branch: if !failedRequired.isEmpty()
    Note over Controller,Service: Source branch: if !principal.hasRole(role)||!principal.hasAuthority(permission)
    Note over Controller,Service: Source branch: if authentication==null||!(authentication.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if request != null && request.getNotes() != null
    Note over Controller,Service: Source branch: if request == null || request.getExpectedRevision() == null || !request.getExpectedRevision().equals(section.getRevision())
    Note over Controller,Service: Source branch: if section.getSubmittedByUserId() != null && section.getSubmittedByUserId().equals(actorId)
    Note over Controller,Service: Source branch: switch sectionCode
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePreparationSectionRepository.saveAndFlush()
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
