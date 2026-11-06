# POST /cruise-package-templates/{id}/versions

```mermaid
sequenceDiagram
    title POST /cruise-package-templates/{id}/versions — CruisePackageController.createVersion
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-package-templates/{id}/versions + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createVersion(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.createVersion()

    Note over Controller,Service: Source branch: for var benefit:benefits
    Note over Controller,Service: Source branch: for var benefit:input.benefits()
    Note over Controller,Service: Source branch: for var inputBenefit:input.benefits()
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,id))
    Note over Controller,Service: Source branch: if !references.add(benefit.resourceType()+":"+benefit.resourceId())
    Note over Controller,Service: Source branch: if benefit.inclusion()==Inclusion.LIMITED_INCLUDED&&benefit.quantityLimit()==null
    Note over Controller,Service: Source branch: if expected==null||actual!=expected
    Note over Controller,Service: Source branch: if new HashSet<>(categories).size()!=categories.size()) fail("FARE_CATEGORY_DUPLICATE","Hạng phòng logic bị trùng."
    Note over Controller,Service: Source branch: if operational
    Note over Controller,Service: Source branch: if value==null) throw new BusinessException(HttpStatus.NOT_FOUND,"FARE_RESOURCE_NOT_FOUND","Không tìm thấy dữ liệu gói vé."
    Note over Controller,Service: Source branch: switch inputBenefit.resourceType()
    Note over Controller,Service: Source branch: switch resourceType(benefit)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.flush()
            Service->>Repository: CruisePackageRepository.increment()
            Service->>Repository: CruisePackageRepository.insert()
            Service->>Repository: CruisePackageRepository.versions()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
