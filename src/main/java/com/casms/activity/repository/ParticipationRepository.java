package com.casms.activity.repository;

import com.casms.activity.entity.Participation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, UUID> {
    Optional<Participation> findByRegistration_RegistrationId(UUID registrationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.participationId = :id")
    Optional<Participation> lockById(@Param("id") UUID id);
}
