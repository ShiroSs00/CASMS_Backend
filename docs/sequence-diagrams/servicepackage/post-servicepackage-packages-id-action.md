# POST /servicepackage/packages/{id}/{action}

```mermaid
sequenceDiagram
    title POST /servicepackage/packages/{id}/{action} — ServicepackageController.packageStatus
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/packages/{id}/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke packageStatus(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.packageStatus()

    Note over Controller,Service: Source branch: if "activate".equals(action)
    Note over Controller,Service: Source branch: if "deactivate".equals(action)
    Note over Controller,Service: Source branch: if p.getStatus()!=TourPackageStatus.ACTIVE)invalid();p.setStatus(TourPackageStatus.INACTIVE
    Note over Controller,Service: Source branch: if p.getStatus()!=TourPackageStatus.DRAFT&&p.getStatus()!=TourPackageStatus.INACTIVE)invalid(
    Note over Controller,Service: Source branch: if packageActivityRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getOnboardActivity().getStatus(
    Note over Controller,Service: Source branch: if packageExcursionRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getShoreExcursion().getStatus(
    Note over Controller,Service: Source branch: if packageServiceRepo.countByTourPackage_TourPackageId(id)+packageActivityRepo.countByTourPackage_TourPackageId(id
    Note over Controller,Service: Source branch: if packageServiceRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getService().getStatus()!=ServiceStatus.ACTIVE)
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourPackageActivityRepository.countByTourPackage_TourPackageId()
            Service->>Repository: TourPackageActivityRepository.findByTourPackage_TourPackageId()
            Service->>Repository: TourPackageExcursionRepository.countByTourPackage_TourPackageId()
            Service->>Repository: TourPackageExcursionRepository.findByTourPackage_TourPackageId()
            Service->>Repository: TourPackageServiceRepository.countByTourPackage_TourPackageId()
            Service->>Repository: TourPackageServiceRepository.findByTourPackage_TourPackageId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
