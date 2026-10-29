# PUT /activities/registrations/{id}

```mermaid
sequenceDiagram
    title PUT /activities/registrations/{id} — ActivityController.updateRegistration
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /activities/registrations/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateRegistration(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.updatePending()

    Note over Controller,Service: Source branch: if !r.getPassenger().getPassengerId().equals(r.getBookingPassenger().getPassenger().getPassengerId())
    Note over Controller,Service: Source branch: if other+quantity>=a.getCapacity())a.setStatus(OnboardActivityStatus.FULL);else if(a.getStatus()==OnboardActivityStatus.FULL
    Note over Controller,Service: Source branch: if other+quantity>=e.getMaximumCapacity())e.setStatus(ShoreExcursionStatus.FULL);else if(e.getStatus()==ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if other+quantity>a.getCapacity())throw new IllegalStateException("Activity capacity exceeded");r.setQuantity(quantity
    Note over Controller,Service: Source branch: if other+quantity>e.getMaximumCapacity())throw new IllegalStateException("Excursion capacity exceeded");r.setQuantity(quantity
    Note over Controller,Service: Source branch: if r.getOnboardActivity()!=null
    Note over Controller,Service: Source branch: if r.getShoreExcursion()!=null
    Note over Controller,Service: Source branch: if r.getStatus()!=RegistrationStatus.PENDING)invalid();if(quantity==null||quantity<1
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.lockActivity()
            Service->>Repository: RegistrationRepository.lockById()
            Service->>Repository: ShoreExcursionRepository.lockById()
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
