package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "\"FarePackageTemplate\"") @Getter @Setter
public class CruisePackageTemplate {
    @Id @Column(name="template_id") private UUID id;
    @Column(name="template_code",nullable=false,length=50,unique=true) private String code;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Version private long revision;
}
