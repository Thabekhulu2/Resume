package com.resume.backend.decisions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDecisionRepository extends JpaRepository<ApplicationDecision, UUID> {
    List<ApplicationDecision> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
