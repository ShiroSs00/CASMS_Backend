package com.casms.booking.repository;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BookingSpecification {

    private BookingSpecification() {
    }

    public static Specification<Booking> filter(UUID cruiseId, BookingStatus status, UUID userId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (cruiseId != null) {
                predicates.add(cb.equal(root.get("cruise").get("cruiseId"), cruiseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("bookingStatus"), status));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("bookedByUser").get("userId"), userId));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
