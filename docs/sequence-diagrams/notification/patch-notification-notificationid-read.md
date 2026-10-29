# PATCH /notification/{notificationId}/read

```mermaid
sequenceDiagram
    title PATCH /notification/{notificationId}/read — NotificationController.markRead
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as NotificationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /notification/{notificationId}/read + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke markRead(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.markRead()

    Note over Controller,Service: Source branch: if notification.getReadAt() == null
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: NotificationRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
