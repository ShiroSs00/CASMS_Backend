# DELETE /servicepackage/packages/{id}/excursions/{excursionId}

```mermaid
sequenceDiagram
    title DELETE /servicepackage/packages/{id}/excursions/{excursionId} — ServicepackageController.removePackageExcursion
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /servicepackage/packages/{id}/excursions/{excursionId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke removePackageExcursion(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.removePackageExcursion()

    Note over Controller,Service: Source branch: if p.getStatus()!=TourPackageStatus.DRAFT||assignmentRepo.existsByTourPackage_TourPackageId(id)
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourPackageExcursionRepository.delete()
            Service->>Repository: TourPackageExcursionRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
