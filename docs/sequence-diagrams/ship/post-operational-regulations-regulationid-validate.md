# POST /operational-regulations/{regulationId}/validate

```mermaid
sequenceDiagram
    title POST /operational-regulations/{regulationId}/validate — OperationalRegulationController.validate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /operational-regulations/{regulationId}/validate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: regulationService.validateRegulation()

    Note over Controller,Service: Source branch: if effectiveFrom == null
    Note over Controller,Service: Source branch: if regulation.getRegulationCode() == null || regulation.getRegulationCode().isBlank()
    Note over Controller,Service: Source branch: if regulation.getRegulationCode().trim().length() > 30
    Note over Controller,Service: Source branch: if regulationId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OperationalRegulationRepository.existsByRegulationCodeAndRegulationIdNot()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
