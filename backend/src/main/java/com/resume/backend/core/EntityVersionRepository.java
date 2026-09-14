package com.resume.backend.core;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityVersionRepository extends JpaRepository<EntityVersion, UUID> {
    Optional<EntityVersion> findByEntityIdAndCurrentTrue(UUID entityId);
}
