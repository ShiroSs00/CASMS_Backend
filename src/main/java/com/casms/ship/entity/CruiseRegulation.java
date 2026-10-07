package com.casms.ship.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"CruiseRegulation\"")
@IdClass(CruiseRegulation.CruiseRegulationId.class)
public class CruiseRegulation {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regulation_id", nullable = false)
    private OperationalRegulation regulation;

    @Getter
    @Setter
    public static class CruiseRegulationId implements Serializable {
        private UUID cruise;
        private UUID regulation;
    }
}
