package com.resume.backend.scoring;

import java.util.Map;

public class EntitySnapshotDto {

    private String id;
    private String entityType;
    private Map<String, Object> data;
    private String versionId;
    private int versionNumber;

    public EntitySnapshotDto() {
    }

    public EntitySnapshotDto(String id, String entityType, Map<String, Object> data, String versionId, int versionNumber) {
        this.id = id;
        this.entityType = entityType;
        this.data = data;
        this.versionId = versionId;
        this.versionNumber = versionNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }
}
