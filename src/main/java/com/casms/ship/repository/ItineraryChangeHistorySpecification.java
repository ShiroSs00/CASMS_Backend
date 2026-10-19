package com.casms.ship.repository;

import com.casms.ship.entity.ItineraryChangeHistory;
import com.casms.ship.enums.ItineraryChangeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItineraryChangeHistorySpecification {

    private ItineraryChangeHistorySpecification() {
    }

    public static Specification<ItineraryChangeHistory> filter(
            UUID itineraryId,
            UUID cruiseStopId,
            ItineraryChangeType changeType,
            UUID changedByUserId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (itineraryId != null) {
                predicates.add(cb.equal(root.get("itinerary").get("itineraryId"), itineraryId));
            }
            if (cruiseStopId != null) {
                predicates.add(cb.equal(root.get("cruiseStop").get("cruiseStopId"), cruiseStopId));
            }
            if (changeType != null) {
                predicates.add(cb.equal(root.get("changeType"), changeType));
            }
            if (changedByUserId != null) {
                predicates.add(cb.equal(root.get("changedByUser").get("userId"), changedByUserId));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
