# POST /cruises/{cruiseId}/preparation/{sectionCode}/submit

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/preparation/{sectionCode}/submit — CruisePreparationController.submitSection
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePreparationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/preparation/{sectionCode}/submit + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submitSection(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: preparationService.submitSection()

    Note over Controller,Service: Source branch: for SectionTemplate template : TEMPLATES
    Note over Controller,Service: Source branch: if !existingCodes.contains(template.code())
    Note over Controller,Service: Source branch: if !principal.hasRole(role) || !principal.hasAuthority(permission)
    Note over Controller,Service: Source branch: if !submissionScope(cruise, actor)
    Note over Controller,Service: Source branch: if "CONFIRMED".equalsIgnoreCase(section.getStatus())
    Note over Controller,Service: Source branch: if "SUBMITTED".equalsIgnoreCase(section.getStatus())
    Note over Controller,Service: Source branch: if TEMPLATES.stream().noneMatch(template -> template.code().equals(sectionCode))
    Note over Controller,Service: Source branch: if request.getEvidence() == null || request.getEvidence().isBlank()
    Note over Controller,Service: Source branch: if request.getNotes() != null
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
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

## CASMS-75 behavior change

Independent section submission retains actual CRM permission, assigned coordinator, section revision, evidence, locks, audit and response. It deliberately does not call global program completeness; incomplete unrelated programs do not block technical/personnel handoff. Global submitDossier is gated through mainflow operations submission.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_PREPARATION_SUBMIT')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as CruisePreparationController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: POST /cruises/{cruiseId}/preparation/{sectionCode}/submit
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: submitSection request
    Service->>Service: Validate coordinator, revision and section evidence; no global program gate
    Service->>Repository: Lock Cruise/section; persist submission and event in existing transaction
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
