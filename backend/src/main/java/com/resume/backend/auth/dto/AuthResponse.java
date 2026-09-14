package com.resume.backend.auth.dto;

import java.util.UUID;

public record AuthResponse(String token, UUID id, String role, String fullName, String email) {
}
