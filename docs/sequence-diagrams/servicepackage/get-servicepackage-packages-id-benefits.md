# GET /servicepackage/packages/{id}/benefits

```mermaid
sequenceDiagram
    title GET /servicepackage/packages/{id}/benefits — ServicepackageController.packageBenefits
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /servicepackage/packages/{id}/benefits + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke packageBenefits(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: catalogReads.entitlements()
        Controller->>Service: service.packageActivities()
        Controller->>Service: service.packageExcursions()
        Controller->>Service: service.packageServices()
        Controller->>Service: service.tourPackage()

    Note over Controller,Service: Source branch: for TourPackage p:packageLineage(id)
    Note over Controller,Service: Source branch: for TourPackageActivity x:packageActivityRepo.findByTourPackage_TourPackageId(p.getTourPackageId())
    Note over Controller,Service: Source branch: for TourPackageExcursion x:packageExcursionRepo.findByTourPackage_TourPackageId(p.getTourPackageId())
    Note over Controller,Service: Source branch: for TourPackageService x:packageServiceRepo.findByTourPackage_TourPackageId(p.getTourPackageId())
    Note over Controller,Service: Source branch: for var entitlement : entitlementRepository.findByTourPackage_TourPackageId(current.getTourPackageId())
    Note over Controller,Service: Source branch: if !seen.add(current.getTourPackageId())) throw new IllegalStateException("Package inheritance cycle detected"
    Note over Controller,Service: Source branch: if !seen.add(current.getTourPackageId()))throw new IllegalStateException("Package inheritance cycle detected"
    Note over Controller,Service: Source branch: if !staff&&p.getStatus()!=TourPackageStatus.ACTIVE)throw new AccessDeniedException("Package is not active"
    Note over Controller,Service: Source branch: while current != null
    Note over Controller,Service: Source branch: while current!=null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PackageEntitlementRepository.findByTourPackage_TourPackageId()
            Service->>Repository: TourPackageActivityRepository.findByTourPackage_TourPackageId()
            Service->>Repository: TourPackageExcursionRepository.findByTourPackage_TourPackageId()
            Service->>Repository: TourPackageRepository.findById()
            Service->>Repository: TourPackageServiceRepository.findByTourPackage_TourPackageId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
