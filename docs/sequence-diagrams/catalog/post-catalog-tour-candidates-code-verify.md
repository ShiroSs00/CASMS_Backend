# POST /catalog/tour-candidates/{code}/verify

```mermaid
sequenceDiagram
    title POST /catalog/tour-candidates/{code}/verify — OperationalTourReviewController.verify
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalTourReviewController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /catalog/tour-candidates/{code}/verify + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke verify(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected

    Note over Controller,Service: Source branch: if !"DRAFT".equals(row.getFirst().get("status"))
    Note over Controller,Service: Source branch: if !portValid || !supplierValid
    Note over Controller,Service: Source branch: if !validEvidenceUrl(input.portEvidenceUrl()) || !validEvidenceUrl(input.supplierEvidenceUrl())
    Note over Controller,Service: Source branch: if actor == null || actor.getUserId() == null
    Note over Controller,Service: Source branch: if input.note().trim().length() < 12 || input.note().trim().length() > 2000
    Note over Controller,Service: Source branch: if row.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tour not found"
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
