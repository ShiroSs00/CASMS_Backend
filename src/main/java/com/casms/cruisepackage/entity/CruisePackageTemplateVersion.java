package com.casms.cruisepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity @Table(name="\"FarePackageTemplateVersion\"") @Getter @Setter
public class CruisePackageTemplateVersion {
    @Id @Column(name="template_version_id") private UUID id;
    @Column(name="template_id",nullable=false) private UUID templateId;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Column(nullable=false,length=200) private String name;
    @Column(columnDefinition="text") private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="logical_categories",nullable=false,columnDefinition="jsonb")
    private List<String> logicalCategories;
    @Column(nullable=false,length=20) private String status;
    @Version private long revision;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="published_by") private UUID publishedBy;
}
