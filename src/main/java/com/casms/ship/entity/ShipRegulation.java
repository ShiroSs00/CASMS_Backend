package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ShipRegulation\"")
@IdClass(ShipRegulation.ShipRegulationId.class)
public class ShipRegulation {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regulation_id", nullable = false)
    private OperationalRegulation regulation;

    @Getter
    @Setter
    public static class ShipRegulationId implements Serializable {
        private UUID ship;
        private UUID regulation;
    }
}
