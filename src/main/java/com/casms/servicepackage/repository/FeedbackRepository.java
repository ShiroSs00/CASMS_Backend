package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    boolean existsByService_ServiceId(UUID serviceId);
}
