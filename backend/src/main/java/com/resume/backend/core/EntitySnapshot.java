package com.resume.backend.core;

import java.util.Map;
import java.util.UUID;

// Mirrors Python's get_entity() return shape: the entity plus its current version's data.
public record EntitySnapshot(UUID id, String entityType, Map<String, Object> data, UUID versionId, int versionNumber) {
}
