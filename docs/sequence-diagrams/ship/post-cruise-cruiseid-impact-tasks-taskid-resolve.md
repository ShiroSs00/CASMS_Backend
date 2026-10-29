# POST /cruise/{cruiseId}/impact-tasks/{taskId}/resolve

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/impact-tasks/{taskId}/resolve — CruiseController.resolveImpactTask
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/impact-tasks/{taskId}/resolve + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke resolveImpactTask(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: cruiseImpactAssessmentService.resolveTask()

    Note over Controller,Service: Source branch: if "RESOLVED".equals(task.getStatus())
    Note over Controller,Service: Source branch: if actorId != null
    Note over Controller,Service: Source branch: if cruiseId != null && !task.getCruise().getCruiseId().equals(cruiseId)
    Note over Controller,Service: Source branch: if expectedRevision != null && !expectedRevision.equals(task.getRevision())
    Note over Controller,Service: Source branch: if resolutionNotes == null || resolutionNotes.trim().length(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseImpactTaskRepository.findById()
            Service->>Repository: CruiseImpactTaskRepository.save()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
