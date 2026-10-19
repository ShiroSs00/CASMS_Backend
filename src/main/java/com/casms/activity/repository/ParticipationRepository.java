package com.casms.activity.repository;

import com.casms.activity.entity.Participation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, UUID> {
    Optional<Participation> findByRegistration_RegistrationId(UUID registrationId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Participation p where p.participationId = :id")
    Optional<Participation> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
