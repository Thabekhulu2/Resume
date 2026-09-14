package com.resume.backend.core;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactTypeRepository extends JpaRepository<FactType, UUID> {
    Optional<FactType> findByKey(String key);
}
