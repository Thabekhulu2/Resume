package com.resume.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("dev-only-change-me-dev-only-change-me", 60);

    @Test
    void issuedTokenParsesBackToTheSameClaims() {
        UUID id = UUID.randomUUID();
        String token = jwtService.issueToken(id, Role.RECRUITER, "recruiter@resume.local");

        var principal = jwtService.parse(token).orElseThrow();

        assertThat(principal.id()).isEqualTo(id);
        assertThat(principal.role()).isEqualTo(Role.RECRUITER);
        assertThat(principal.email()).isEqualTo("recruiter@resume.local");
    }

    @Test
    void garbageTokenFailsToParse() {
        assertThat(jwtService.parse("not-a-real-token")).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtService otherIssuer = new JwtService("a-completely-different-dev-secret-value", 60);
        String token = otherIssuer.issueToken(UUID.randomUUID(), Role.CANDIDATE, "candidate@resume.local");

        assertThat(jwtService.parse(token)).isEmpty();
    }
}
