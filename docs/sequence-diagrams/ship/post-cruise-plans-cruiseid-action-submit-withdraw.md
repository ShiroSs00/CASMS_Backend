# POST /cruise-plans/{cruiseId}/{action:submit|withdraw}

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/{action:submit|withdraw} — CruiseMainflowController.submit
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseMainflowController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/{action:submit|withdraw} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submit(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.transition()

    Note over Controller,Service: Source branch: if !"APPROVE".equals(action
    Note over Controller,Service: Source branch: if !"SUBMITTED".equals(before) && !reopening) throw conflict(
    Note over Controller,Service: Source branch: if !"SUBMITTED".equals(before)) throw conflict(
    Note over Controller,Service: Source branch: if !"[]".equals(visits)) out.put("destinationVisits", json(visits)
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(guidance)) b.add("GUIDANCE_PUBLICATION_REQUIRED"
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(onboard)) b.add("ONBOARD_STAFF_REQUIRED"
    Note over Controller,Service: Source branch: if !Objects.equals(r.get("fingerprint"), hash(snapshot)
    Note over Controller,Service: Source branch: if !Set.of("DRAFT", "WITHDRAWN", "REVISION_REQUESTED").contains(before)
    Note over Controller,Service: Source branch: if !Set.of("PENDING_APPROVAL", "APPROVED"
    Note over Controller,Service: Source branch: if !approved(id, r)) b.add("PLAN_APPROVAL_REQUIRED"
    Note over Controller,Service: Source branch: if !blockers.isEmpty()
    Note over Controller,Service: Source branch: if !operational.isReady()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```

## CASMS-75 behavior change

Draft create/update and feasibility checks retain calendar/day validation in ship timezone without full activity coverage. Submit/approve/publish call shared program completeness; mainflow plan/ready blockers and transition responses reflect current issues. Direct itinerary transitions serialize via Cruise then Itinerary locks. Departmental submissions remain independent. Existing maker-checker, pricing, readiness, state/revision checks and response shape remain; incomplete operations submission is 422 and no state is advanced.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `authenticated (SecurityConfig anyRequest); no handler authority annotation` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as CruiseMainflowController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: POST /cruise-plans/{cruiseId}/{action:submit|withdraw}
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: submit request
    Service->>Service: Retain existing rules; collect current completeness issues at operations boundary
    Service->>Repository: Bound reads of programs/stops/areas/destinations/supplier; transition writes only if all gates pass
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
