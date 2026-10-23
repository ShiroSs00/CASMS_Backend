package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.entity.ModelImportJob;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ModelImportStatus;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.ModelImportJobRepository;
import com.casms.ship.repository.ShipModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModelImportJobLifecycle {
    private final ShipModelRepository models;
    private final ModelImportJobRepository jobs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelImportJob start(UUID shipId, UUID modelId, String requestedBy) {
        ShipModel model = models.findById(modelId)
                .filter(candidate -> candidate.getShip().getShipId().equals(shipId) && candidate.getModelKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Ship model", modelId));
        if (model.getStatus() != ShipModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model structure can only be imported while the model is DRAFT");
        }
        ModelImportJob job = new ModelImportJob();
        job.setShipModel(model);
        job.setRequestedBy(requestedBy);
        job.setStatus(ModelImportStatus.RUNNING);
        return jobs.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, RuntimeException failure) {
        ModelImportJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalStateException("Import job disappeared", failure));
        job.setStatus(ModelImportStatus.FAILED);
        job.setFinishedAt(Instant.now());
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        job.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        jobs.save(job);
    }
}
