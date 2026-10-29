# POST /servicepackage/packages/{id}/activities/{activityId}

```mermaid
sequenceDiagram
    title POST /servicepackage/packages/{id}/activities/{activityId} — ServicepackageController.addPackageActivity
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/packages/{id}/activities/{activityId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke addPackageActivity(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.addPackageActivity()

    Note over Controller,Service: Source branch: if benefit==null)throw new IllegalArgumentException("Benefit type is required"
    Note over Controller,Service: Source branch: if p.getStatus()!=TourPackageStatus.DRAFT||assignmentRepo.existsByTourPackage_TourPackageId(id)
    Note over Controller,Service: Source branch: if packageActivityRepo.existsById(key))throw new IllegalStateException("Package activity already exists"
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.findById()
            Service->>Repository: TourPackageActivityRepository.existsById()
            Service->>Repository: TourPackageActivityRepository.save()
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
