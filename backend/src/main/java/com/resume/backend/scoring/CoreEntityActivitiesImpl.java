package com.resume.backend.scoring;

import com.resume.backend.core.CoreEntityService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CoreEntityActivitiesImpl implements CoreEntityActivities {

    private final CoreEntityService coreEntityService;

    public CoreEntityActivitiesImpl(CoreEntityService coreEntityService) {
        this.coreEntityService = coreEntityService;
    }

    @Override
    public EntityResultDto createEntity(String entityType, Map<String, Object> attributes) {
        var result = coreEntityService.createEntity(entityType, attributes);
        return new EntityResultDto(result.entityId().toString(), result.versionId().toString());
    }

    @Override
    public EntityResultDto updateEntityScd2(String entityId, Map<String, Object> attributes) {
        var result = coreEntityService.updateEntityScd2(UUID.fromString(entityId), attributes);
        return new EntityResultDto(result.entityId().toString(), result.versionId().toString());
    }

    @Override
    public EntitySnapshotDto getEntity(String entityId) {
        var snapshot = coreEntityService.getEntity(UUID.fromString(entityId));
        return new EntitySnapshotDto(
                snapshot.id().toString(), snapshot.entityType(), snapshot.data(),
                snapshot.versionId().toString(), snapshot.versionNumber());
    }

    @Override
    public String createRelationship(String fromEntityId, String toEntityId, String relationshipType, Map<String, Object> attributes) {
        return coreEntityService.createRelationship(
                UUID.fromString(fromEntityId), UUID.fromString(toEntityId), relationshipType, attributes).toString();
    }

    @Override
    public String upsertEntityFact(String entityId, String factTypeKey, double value, Map<String, Object> metadata) {
        return coreEntityService.upsertEntityFact(UUID.fromString(entityId), factTypeKey, value, metadata).toString();
    }
}
