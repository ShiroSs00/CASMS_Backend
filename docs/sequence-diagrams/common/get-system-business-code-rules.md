# GET /system/business-code-rules

```mermaid
sequenceDiagram
    title GET /system/business-code-rules — BusinessCodeRuleController.getAllRules
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BusinessCodeRuleController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /system/business-code-rules + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getAllRules(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: ruleService.getAllRules()

    Note over Controller,Service: Source branch: if "CRUISE".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "EXCURSION".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "SHIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "TRIP".equalsIgnoreCase(entityType)
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
