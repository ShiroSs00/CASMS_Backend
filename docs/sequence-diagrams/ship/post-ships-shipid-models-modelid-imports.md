# POST /ships/{shipId}/models/{modelId}/imports

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/models/{modelId}/imports — ShipModelController.importStructure
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipModelController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/models/{modelId}/imports + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke importStructure(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: importService.importModel()

    Note over Controller,Service: Source branch: for JsonNode area : manifest.path("areas")
    Note over Controller,Service: Source branch: for JsonNode source : manifest.path("cabins")
    Note over Controller,Service: Source branch: for JsonNode source : manifest.path("decks")
    Note over Controller,Service: Source branch: for JsonNode source : manifest.path("serviceDecks")
    Note over Controller,Service: Source branch: for double dimension : size
    Note over Controller,Service: Source branch: for int i = 0; i < glb.path("nodes").size(); i++
    Note over Controller,Service: Source branch: for var entry : areaSources.entrySet()
    Note over Controller,Service: Source branch: if !"area".equals(node.path("extras").path("entityType").asText())
    Note over Controller,Service: Source branch: if !Objects.equals(cabin.getDeck().getDeckId(), deck.getDeckId())
    Note over Controller,Service: Source branch: if !Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())
    Note over Controller,Service: Source branch: if !areaNodes.containsKey(id)
    Note over Controller,Service: Source branch: if !areaNodes.keySet().equals(areaSources.keySet())
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ObjectImportException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws ResponseStatusException; HTTP mapping requires exception-handler review
    end
```
