package com.resume.backend.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "relationships_v2")
public class Relationship {

    @Id
    private UUID id;

    @Column(name = "relationship_type", nullable = false)
    private String relationshipType;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    // Managed entirely by the relationships_v2 SCD2 trigger (set_relationship_current_flag).
    @Column(name = "is_current", insertable = false, updatable = false)
    private boolean current;

    protected Relationship() {
    }

    public Relationship(UUID id, String relationshipType, UUID parentId, UUID childId, Map<String, Object> metadata) {
        this.id = id;
        this.relationshipType = relationshipType;
        this.parentId = parentId;
        this.childId = childId;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getChildId() {
        return childId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isCurrent() {
        return current;
    }
}
