# POST /notification/{notificationId}/ack

```mermaid
sequenceDiagram
    title POST /notification/{notificationId}/ack — NotificationController.acknowledgeNotification
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as NotificationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /notification/{notificationId}/ack + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke acknowledgeNotification(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.acknowledgeNotification()

    Note over Controller,Service: Source branch: for ImpactNotificationOutbox o : outboxes
    Note over Controller,Service: Source branch: if notification.getReadAt() == null
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
    Note over Controller,Service: Source branch: if remainingUnack == 0
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseImpactTaskRepository.save()
            Service->>Repository: ImpactNotificationOutboxRepository.countByCruise_CruiseIdAndStatus()
            Service->>Repository: ImpactNotificationOutboxRepository.findByRecipientUser_UserIdAndAcknowledgedAtIsNull()
            Service->>Repository: ImpactNotificationOutboxRepository.save()
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
