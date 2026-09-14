package com.resume.backend.scoring;

public class EntityResultDto {

    private String entityId;
    private String versionId;

    public EntityResultDto() {
    }

    public EntityResultDto(String entityId, String versionId) {
        this.entityId = entityId;
        this.versionId = versionId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }
}
