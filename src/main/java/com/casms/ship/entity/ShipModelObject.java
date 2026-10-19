package com.casms.ship.entity;

import com.casms.ship.enums.ShipModelObjectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ShipModelObject\"", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ship_model_object_node", columnNames = {"ship_model_id", "node_key"})
}, indexes = {
        @Index(name = "idx_ship_model_object_deck", columnList = "deck_id"),
        @Index(name = "idx_ship_model_object_cabin", columnList = "cabin_id")
})
public class ShipModelObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ship_model_object_id")
    private UUID shipModelObjectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_model_id", nullable = false)
    private ShipModel shipModel;

    @Column(name = "node_key", nullable = false, length = 200)
    private String nodeKey;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 30)
    private ShipModelObjectType objectType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_id")
    private Cabin cabin;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
