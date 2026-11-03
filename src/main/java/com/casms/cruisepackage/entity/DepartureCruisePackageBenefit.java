package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="\"DepartureFarePackageBenefit\"") @Getter @Setter
public class DepartureCruisePackageBenefit {
    @Id @Column(name="binding_id") private UUID id;
    @Column(name="offer_id",nullable=false) private UUID offerId;
    @Column(name="benefit_id",nullable=false) private UUID benefitId;
    @Column(name="onboard_activity_id") private UUID onboardActivityId;
    @Column(name="shore_excursion_id") private UUID shoreExcursionId;
    @Column(name="service_id") private UUID serviceId;
}
