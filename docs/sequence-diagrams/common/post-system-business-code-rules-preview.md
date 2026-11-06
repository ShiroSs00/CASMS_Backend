# POST /system/business-code-rules/preview

```mermaid
sequenceDiagram
    title POST /system/business-code-rules/preview — BusinessCodeRuleController.previewCode
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BusinessCodeRuleController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /system/business-code-rules/preview + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke previewCode(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: ruleService.previewCode()

    Note over Controller,Service: Source branch: for Map.Entry<String, String> entry : context.entrySet()
    Note over Controller,Service: Source branch: if !seqMatcher.find()
    Note over Controller,Service: Source branch: if "CRUISE".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "EXCURSION".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "SHIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if "TRIP".equalsIgnoreCase(entityType)
    Note over Controller,Service: Source branch: if entry.getValue() != null
    Note over Controller,Service: Source branch: if matcher.find()
    Note over Controller,Service: Source branch: if matcher.group(1) != null
    Note over Controller,Service: Source branch: if request.getSampleContext() != null
    Note over Controller,Service: Source branch: if scopeStrategy == null
    Note over Controller,Service: Source branch: switch scopeStrategy.toUpperCase()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
