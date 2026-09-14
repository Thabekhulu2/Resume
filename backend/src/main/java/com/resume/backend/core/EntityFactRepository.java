package com.resume.backend.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityFactRepository extends JpaRepository<EntityFact, UUID> {
}
