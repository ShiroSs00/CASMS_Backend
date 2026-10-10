package com.casms.ship.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.ItineraryChangeType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ItineraryChangeHistory\"")
public class ItineraryChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "change_history_id")
    private UUID changeHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_stop_id")
    private CruiseStop cruiseStop;

    @Column(name = "changed_entity_type", nullable = false, length = 50)
    private String changedEntityType;

    @Column(name = "changed_entity_id", nullable = false)
    private UUID changedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ItineraryChangeType changeType;

    @Type(JsonBinaryType.class)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;

    @Type(JsonBinaryType.class)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private String afterData;

    @Column(name = "change_summary", nullable = false, length = 1000)
    private String changeSummary;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedByUser;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
