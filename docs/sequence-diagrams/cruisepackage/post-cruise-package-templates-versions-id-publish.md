# POST /cruise-package-templates/versions/{id}/publish

```mermaid
sequenceDiagram
    title POST /cruise-package-templates/versions/{id}/publish — CruisePackageController.publish
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-package-templates/versions/{id}/publish + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke publish(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.publish()

    Note over Controller,Service: Source branch: for var benefit:benefits
    Note over Controller,Service: Source branch: for var benefit:repository.benefits(versionId)
    Note over Controller,Service: Source branch: for var existing:repository.versions(template.getId())
    Note over Controller,Service: Source branch: if !"DRAFT".equals(version.getStatus())) fail("FARE_VERSION_IMMUTABLE","Phiên bản đã phát hành không được sửa hoặc phát hành lại."
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,id))
    Note over Controller,Service: Source branch: if !blockers.isEmpty()
    Note over Controller,Service: Source branch: if "PUBLISHED".equals(existing.getStatus())
    Note over Controller,Service: Source branch: if existing.getVersionNumber()>=version.getVersionNumber()
    Note over Controller,Service: Source branch: if expected==null||actual!=expected
    Note over Controller,Service: Source branch: if value==null) throw new BusinessException(HttpStatus.NOT_FOUND,"FARE_RESOURCE_NOT_FOUND","Không tìm thấy dữ liệu gói vé."
    Note over Controller,Service: Source branch: switch resourceType(benefit)
    Note over Controller,Service: Source branch: switch type
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.benefits()
            Service->>Repository: CruisePackageRepository.flush()
            Service->>Repository: CruisePackageRepository.increment()
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
