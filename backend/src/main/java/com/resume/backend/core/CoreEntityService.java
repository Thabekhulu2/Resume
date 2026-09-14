package com.resume.backend.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Java equivalent of temporal/src/activities/supabase_core.py's five functions,
// now as plain JPA/@Transactional service methods against the same tables
// instead of PostgREST calls through the Supabase client.
@Service
public class CoreEntityService {

    private final EntityRepository entityRepository;
    private final EntityVersionRepository entityVersionRepository;
    private final RelationshipRepository relationshipRepository;
    private final FactTypeRepository factTypeRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public CoreEntityService(
            EntityRepository entityRepository,
            EntityVersionRepository entityVersionRepository,
            RelationshipRepository relationshipRepository,
            FactTypeRepository factTypeRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.entityRepository = entityRepository;
        this.entityVersionRepository = entityVersionRepository;
        this.relationshipRepository = relationshipRepository;
        this.factTypeRepository = factTypeRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public record EntityResult(UUID entityId, UUID versionId) {
    }

    @Transactional
    public EntityResult createEntity(String entityType, Map<String, Object> attributes) {
        return createEntity(entityType, attributes, null);
    }

    @Transactional
    public EntityResult createEntity(String entityType, Map<String, Object> attributes, UUID applicantId) {
        UUID entityId = UUID.randomUUID();
        entityRepository.save(new EntityRecord(entityId, entityType, applicantId));

        UUID versionId = UUID.randomUUID();
        entityVersionRepository.save(new EntityVersion(versionId, entityId, 1, attributes));

        return new EntityResult(entityId, versionId);
    }

    // Relies on the entity_versions SCD2 trigger (set_entity_version_validity)
    // to close the previous current version -- this just inserts the next one.
    @Transactional
    public EntityResult updateEntityScd2(UUID entityId, Map<String, Object> attributes) {
        EntityVersion current = entityVersionRepository.findByEntityIdAndCurrentTrue(entityId)
                .orElseThrow(() -> new NoSuchElementException("No current version for entity " + entityId));

        UUID versionId = UUID.randomUUID();
        entityVersionRepository.save(new EntityVersion(versionId, entityId, current.getVersionNumber() + 1, attributes));

        return new EntityResult(entityId, versionId);
    }

    @Transactional(readOnly = true)
    public EntitySnapshot getEntity(UUID entityId) {
        EntityRecord entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityId));
        EntityVersion version = entityVersionRepository.findByEntityIdAndCurrentTrue(entityId)
                .orElseThrow(() -> new NoSuchElementException("No current version for entity " + entityId));

        return new EntitySnapshot(entity.getId(), entity.getEntityType(), version.getData(), version.getId(), version.getVersionNumber());
    }

    @Transactional
    public UUID createRelationship(UUID fromEntityId, UUID toEntityId, String relationshipType, Map<String, Object> attributes) {
        UUID relationshipId = UUID.randomUUID();
        relationshipRepository.save(new Relationship(
                relationshipId, relationshipType, fromEntityId, toEntityId, attributes != null ? attributes : Map.of()));
        return relationshipId;
    }

    // JPA has no clean ON CONFLICT support, so this is a native upsert matching
    // the Python side's .upsert(..., on_conflict="entity_id,fact_type_id,dimension_id").
    @Transactional
    public UUID upsertEntityFact(UUID entityId, String factTypeKey, double value, Map<String, Object> metadata) {
        FactType factType = factTypeRepository.findByKey(factTypeKey)
                .orElseThrow(() -> new NoSuchElementException("Unknown fact type: " + factTypeKey));

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Metadata is not valid JSON", e);
        }

        Object id = entityManager.createNativeQuery("""
                        insert into entity_facts (entity_id, fact_type_id, value, metadata)
                        values (:entityId, :factTypeId, :value, cast(:metadata as jsonb))
                        on conflict (entity_id, fact_type_id, dimension_id)
                        do update set value = excluded.value, metadata = excluded.metadata, updated_at = now()
                        returning id
                        """)
                .setParameter("entityId", entityId)
                .setParameter("factTypeId", factType.getId())
                .setParameter("value", value)
                .setParameter("metadata", metadataJson)
                .getSingleResult();

        return id instanceof UUID uuid ? uuid : UUID.fromString(id.toString());
    }
}
