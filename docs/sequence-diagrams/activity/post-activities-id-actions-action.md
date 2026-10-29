# POST /activities/{id}/actions/{action}

```mermaid
sequenceDiagram
    title POST /activities/{id}/actions/{action} — ActivityController.lifecycle
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/{id}/actions/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke lifecycle(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.lifecycle()

    Note over Controller,Service: Source branch: if a.getActivityCode()==null || a.getActivityCode().isBlank() || a.getActivityName()==null || a.getActivityName().isBlank()
    Note over Controller,Service: Source branch: if a.getCapacity()==null || a.getCapacity()<1) throw new IllegalArgumentException("Capacity must be positive"
    Note over Controller,Service: Source branch: if a.getCruise()!=null
    Note over Controller,Service: Source branch: if a.getCruise()!=null&&a.getLocation()!=null&&!a.getLocation().isBlank()&&activities.countScheduleConflicts(a.getCruise(
    Note over Controller,Service: Source branch: if a.getPrice()!=null && (a.getPrice().signum()<0 || a.getCurrencyCode()==null || a.getCurrencyCode().isBlank())
    Note over Controller,Service: Source branch: if a.getStartTime()==null || a.getEndTime()==null || !a.getEndTime().isAfter(a.getStartTime())
    Note over Controller,Service: Source branch: if a.getStatus()!=OnboardActivityStatus.IN_PROGRESS) invalid(); a.setStatus(OnboardActivityStatus.COMPLETED
    Note over Controller,Service: Source branch: if a.getStatus()!=OnboardActivityStatus.OPEN && a.getStatus()!=OnboardActivityStatus.FULL) invalid(); if (a.getStartTime(
    Note over Controller,Service: Source branch: if a.getStatus()!=OnboardActivityStatus.PLANNED) invalid(); validateActivity(a,id); a.setStatus(OnboardActivityStatus.OPEN
    Note over Controller,Service: Source branch: if a.getStatus()==OnboardActivityStatus.COMPLETED || a.getStatus()==OnboardActivityStatus.CANCELLED) invalid(
    Note over Controller,Service: Source branch: if departure==null||end==null||a.getStartTime().isBefore(departure)||a.getEndTime().isAfter(end)
    Note over Controller,Service: Source branch: switch action
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws UnsupportedOperationException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws com.casms.common.exception.BusinessException; HTTP mapping requires exception-handler review
    end
```
