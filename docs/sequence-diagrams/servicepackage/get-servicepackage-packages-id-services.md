# GET /servicepackage/packages/{id}/services

```mermaid
sequenceDiagram
    title GET /servicepackage/packages/{id}/services — ServicepackageController.packageServices
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /servicepackage/packages/{id}/services + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke packageServices(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.packageServices()

    Note over Controller,Service: Source branch: for TourPackage p:packageLineage(id)
    Note over Controller,Service: Source branch: for TourPackageService x:packageServiceRepo.findByTourPackage_TourPackageId(p.getTourPackageId())
    Note over Controller,Service: Source branch: if !seen.add(current.getTourPackageId()))throw new IllegalStateException("Package inheritance cycle detected"
    Note over Controller,Service: Source branch: while current!=null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourPackageServiceRepository.findByTourPackage_TourPackageId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
