# GET /catalog/package-benefits/{code}

```mermaid
sequenceDiagram
    title GET /catalog/package-benefits/{code} — PackageBenefitReadController.detail
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PackageBenefitReadController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /catalog/package-benefits/{code} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke detail(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected

    Note over Controller,Service: Source branch: for Benefit b : benefits
    Note over Controller,Service: Source branch: if "INCLUDED".equals(b.benefitType()) && !configuredQuota(b.quantityLimit(), b.unitCode())
    Note over Controller,Service: Source branch: if benefits.isEmpty()) blockers.add("PACKAGE_BENEFITS_MISSING"
    Note over Controller,Service: Source branch: if packages.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
