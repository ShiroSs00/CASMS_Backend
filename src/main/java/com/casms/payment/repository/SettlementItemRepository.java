package com.casms.payment.repository;

import com.casms.payment.entity.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementItemRepository extends JpaRepository<SettlementItem, UUID> {
    List<SettlementItem> findByFinalSettlement_FinalSettlementIdOrderByOccurredAtAsc(
            UUID finalSettlementId);
}

