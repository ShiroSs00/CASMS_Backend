package com.casms.checkin.repository;

import com.casms.checkin.entity.GuidanceSessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GuidanceSessionParticipantRepository extends JpaRepository<GuidanceSessionParticipant, UUID> {
}
