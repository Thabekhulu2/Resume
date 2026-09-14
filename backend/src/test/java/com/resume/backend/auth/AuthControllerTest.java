package com.resume.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resume.backend.auth.dto.LoginRequest;
import com.resume.backend.auth.dto.SignupRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {

    private RecruiterRepository recruiterRepository;
    private CandidateRepository candidateRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        recruiterRepository = mock(RecruiterRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        controller = new AuthController(recruiterRepository, candidateRepository, passwordEncoder, jwtService);
    }

    @Test
    void recruiterLoginSucceedsWithMatchingPassword() {
        Recruiter recruiter = new Recruiter(UUID.randomUUID(), "Dev Recruiter", "recruiter@resume.local", "hashed");
        when(recruiterRepository.findByEmail("recruiter@resume.local")).thenReturn(Optional.of(recruiter));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.issueToken(recruiter.getId(), Role.RECRUITER, recruiter.getEmail())).thenReturn("a-jwt");

        var response = controller.recruiterLogin(new LoginRequest("recruiter@resume.local", "password123"));

        assertThat(response.token()).isEqualTo("a-jwt");
        assertThat(response.role()).isEqualTo("RECRUITER");
    }

    @Test
    void recruiterLoginRejectsWrongPassword() {
        Recruiter recruiter = new Recruiter(UUID.randomUUID(), "Dev Recruiter", "recruiter@resume.local", "hashed");
        when(recruiterRepository.findByEmail("recruiter@resume.local")).thenReturn(Optional.of(recruiter));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> controller.recruiterLogin(new LoginRequest("recruiter@resume.local", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void recruiterLoginRejectsUnknownEmail() {
        when(recruiterRepository.findByEmail("nobody@resume.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.recruiterLogin(new LoginRequest("nobody@resume.local", "whatever")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void candidateSignupRejectsDuplicateEmail() {
        when(candidateRepository.existsByEmail("dup@resume.local")).thenReturn(true);

        assertThatThrownBy(() -> controller.candidateSignup(new SignupRequest("dup@resume.local", "password123", "Someone")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void candidateSignupCreatesAccountAndIssuesToken() {
        when(candidateRepository.existsByEmail("new@resume.local")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.issueToken(any(UUID.class), any(Role.class), any(String.class))).thenReturn("a-jwt");

        var response = controller.candidateSignup(new SignupRequest("new@resume.local", "password123", "New Candidate"));

        assertThat(response.token()).isEqualTo("a-jwt");
        assertThat(response.email()).isEqualTo("new@resume.local");
        assertThat(response.role()).isEqualTo("CANDIDATE");
    }
}
