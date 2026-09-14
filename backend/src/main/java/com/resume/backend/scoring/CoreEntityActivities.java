package com.resume.backend.scoring;

import io.temporal.activity.ActivityInterface;
import java.util.Map;

// Temporal-boundary wrapper around CoreEntityService (Phase 2) -- same five
// operations as temporal/src/activities/supabase_core.py, using String ids
// and DTOs instead of UUID/Java records to stay safely Jackson-serializable
// across the Temporal workflow/activity boundary.
@ActivityInterface
public interface CoreEntityActivities {
    EntityResultDto createEntity(String entityType, Map<String, Object> attributes);

    EntityResultDto updateEntityScd2(String entityId, Map<String, Object> attributes);

    EntitySnapshotDto getEntity(String entityId);

    String createRelationship(String fromEntityId, String toEntityId, String relationshipType, Map<String, Object> attributes);

    String upsertEntityFact(String entityId, String factTypeKey, double value, Map<String, Object> metadata);
}
