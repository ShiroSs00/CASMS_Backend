# GET /system/business-code-rules/{entityType}

```mermaid
sequenceDiagram
    title GET /system/business-code-rules/{entityType} — BusinessCodeRuleController.getRuleByEntityType
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BusinessCodeRuleController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /system/business-code-rules/{entityType} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getRuleByEntityType(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: ruleService.getRuleByEntityType()

    Note over Controller,Service: Source branch: if "CRUISE".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "EXCURSION".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "SHIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "TRIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if list.isEmpty()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
