# POST /cruises/{cruiseId}/preparation/{sectionCode}/return

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/preparation/{sectionCode}/return — CruisePreparationController.returnSection
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePreparationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/preparation/{sectionCode}/return + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke returnSection(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: preparationService.returnSection()

    Note over Controller,Service: Source branch: if !"SUBMITTED".equalsIgnoreCase(section.getStatus()) && !"CONFIRMED".equalsIgnoreCase(section.getStatus())
    Note over Controller,Service: Source branch: if !principal.hasRole(role)||!principal.hasAuthority(permission)
    Note over Controller,Service: Source branch: if authentication==null||!(authentication.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if request == null || request.getExpectedRevision() == null || !request.getExpectedRevision().equals(section.getRevision())
    Note over Controller,Service: Source branch: if request.getNotes() != null
    Note over Controller,Service: Source branch: if request.getReason()==null||request.getReason().isBlank()
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
