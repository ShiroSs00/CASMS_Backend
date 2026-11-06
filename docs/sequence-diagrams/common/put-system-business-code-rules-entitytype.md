# PUT /system/business-code-rules/{entityType}

```mermaid
sequenceDiagram
    title PUT /system/business-code-rules/{entityType} — BusinessCodeRuleController.updateRule
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BusinessCodeRuleController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /system/business-code-rules/{entityType} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateRule(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: ruleService.updateRule()

    Note over Controller,Service: Source branch: for Map.Entry<String, String> entry : context.entrySet()
    Note over Controller,Service: Source branch: if "CRUISE".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "EXCURSION".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "SHIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "TRIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if context != null
    Note over Controller,Service: Source branch: if entry.getValue() != null
    Note over Controller,Service: Source branch: if list.isEmpty()
    Note over Controller,Service: Source branch: if matcher.find()
    Note over Controller,Service: Source branch: if matcher.group(1) != null
    Note over Controller,Service: Source branch: if rows.isEmpty()
    Note over Controller,Service: Source branch: if scopeStrategy == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
