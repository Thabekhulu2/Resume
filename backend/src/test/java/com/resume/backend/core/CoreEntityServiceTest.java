package com.resume.backend.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CoreEntityServiceTest {

    private EntityRepository entityRepository;
    private EntityVersionRepository entityVersionRepository;
    private RelationshipRepository relationshipRepository;
    private FactTypeRepository factTypeRepository;
    private EntityManager entityManager;
    private CoreEntityService service;

    @BeforeEach
    void setUp() {
        entityRepository = mock(EntityRepository.class);
        entityVersionRepository = mock(EntityVersionRepository.class);
        relationshipRepository = mock(RelationshipRepository.class);
        factTypeRepository = mock(FactTypeRepository.class);
        entityManager = mock(EntityManager.class);
        service = new CoreEntityService(
                entityRepository, entityVersionRepository, relationshipRepository, factTypeRepository,
                entityManager, new ObjectMapper());
    }

    @Test
    void createEntitySavesEntityAndFirstVersion() {
        var result = service.createEntity("candidate", Map.of("name", "Jane"));

        ArgumentCaptor<EntityRecord> entityCaptor = ArgumentCaptor.forClass(EntityRecord.class);
        verify(entityRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEntityType()).isEqualTo("candidate");
        assertThat(entityCaptor.getValue().getId()).isEqualTo(result.entityId());

        ArgumentCaptor<EntityVersion> versionCaptor = ArgumentCaptor.forClass(EntityVersion.class);
        verify(entityVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getEntityId()).isEqualTo(result.entityId());
        assertThat(versionCaptor.getValue().getData()).containsEntry("name", "Jane");
    }

    @Test
    void updateEntityScd2IncrementsVersionNumberFromCurrent() {
        UUID entityId = UUID.randomUUID();
        EntityVersion current = new EntityVersion(UUID.randomUUID(), entityId, 3, Map.of());
        when(entityVersionRepository.findByEntityIdAndCurrentTrue(entityId)).thenReturn(Optional.of(current));

        var result = service.updateEntityScd2(entityId, Map.of("status", "scored"));

        ArgumentCaptor<EntityVersion> versionCaptor = ArgumentCaptor.forClass(EntityVersion.class);
        verify(entityVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(4);
        assertThat(result.entityId()).isEqualTo(entityId);
    }

    @Test
    void updateEntityScd2ThrowsWhenNoCurrentVersionExists() {
        UUID entityId = UUID.randomUUID();
        when(entityVersionRepository.findByEntityIdAndCurrentTrue(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEntityScd2(entityId, Map.of()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getEntityCombinesEntityAndCurrentVersion() {
        UUID entityId = UUID.randomUUID();
        EntityRecord entity = new EntityRecord(entityId, "candidate");
        EntityVersion version = new EntityVersion(UUID.randomUUID(), entityId, 2, Map.of("status", "scored"));
        when(entityRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(entityVersionRepository.findByEntityIdAndCurrentTrue(entityId)).thenReturn(Optional.of(version));

        EntitySnapshot snapshot = service.getEntity(entityId);

        assertThat(snapshot.id()).isEqualTo(entityId);
        assertThat(snapshot.entityType()).isEqualTo("candidate");
        assertThat(snapshot.versionNumber()).isEqualTo(2);
        assertThat(snapshot.data()).containsEntry("status", "scored");
    }

    @Test
    void getEntityThrowsWhenEntityMissing() {
        UUID entityId = UUID.randomUUID();
        when(entityRepository.findById(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEntity(entityId)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void createRelationshipSavesWithGivenFields() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        UUID relationshipId = service.createRelationship(from, to, "candidate_scored_against_job", null);

        ArgumentCaptor<Relationship> captor = ArgumentCaptor.forClass(Relationship.class);
        verify(relationshipRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(relationshipId);
        assertThat(captor.getValue().getParentId()).isEqualTo(from);
        assertThat(captor.getValue().getChildId()).isEqualTo(to);
        assertThat(captor.getValue().getRelationshipType()).isEqualTo("candidate_scored_against_job");
        assertThat(captor.getValue().getMetadata()).isEmpty();
    }

    @Test
    void upsertEntityFactLooksUpFactTypeAndRunsNativeUpsert() {
        UUID entityId = UUID.randomUUID();
        UUID factTypeId = UUID.randomUUID();
        UUID expectedFactId = UUID.randomUUID();
        FactType factType = mock(FactType.class);
        when(factType.getId()).thenReturn(factTypeId);
        when(factTypeRepository.findByKey("jd_fit_score")).thenReturn(Optional.of(factType));

        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(expectedFactId);

        UUID factId = service.upsertEntityFact(entityId, "jd_fit_score", 85.0, Map.of("reasoning", "good fit"));

        assertThat(factId).isEqualTo(expectedFactId);
        verify(query).setParameter(eq("entityId"), eq(entityId));
        verify(query).setParameter(eq("factTypeId"), eq(factTypeId));
        verify(query).setParameter(eq("value"), eq(85.0));
    }

    @Test
    void upsertEntityFactThrowsWhenFactTypeUnknown() {
        when(factTypeRepository.findByKey("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertEntityFact(UUID.randomUUID(), "nonexistent", 1.0, null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
