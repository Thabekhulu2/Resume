package com.resume.backend.auth;

import com.resume.backend.auth.dto.AuthResponse;
import com.resume.backend.auth.dto.LoginRequest;
import com.resume.backend.auth.dto.SignupRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RecruiterRepository recruiterRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            RecruiterRepository recruiterRepository,
            CandidateRepository candidateRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.recruiterRepository = recruiterRepository;
        this.candidateRepository = candidateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/recruiter/login")
    public AuthResponse recruiterLogin(@Valid @RequestBody LoginRequest request) {
        Recruiter recruiter = recruiterRepository.findByEmail(request.email())
                .filter(r -> passwordEncoder.matches(request.password(), r.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.issueToken(recruiter.getId(), Role.RECRUITER, recruiter.getEmail());
        return new AuthResponse(token, recruiter.getId(), Role.RECRUITER.name(), recruiter.getFullName(), recruiter.getEmail());
    }

    @PostMapping("/candidate/login")
    public AuthResponse candidateLogin(@Valid @RequestBody LoginRequest request) {
        Candidate candidate = candidateRepository.findByEmail(request.email())
                .filter(c -> passwordEncoder.matches(request.password(), c.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.issueToken(candidate.getId(), Role.CANDIDATE, candidate.getEmail());
        return new AuthResponse(token, candidate.getId(), Role.CANDIDATE.name(), candidate.getFullName(), candidate.getEmail());
    }

    @PostMapping("/candidate/signup")
    public AuthResponse candidateSignup(@Valid @RequestBody SignupRequest request) {
        if (candidateRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        Candidate candidate = candidateRepository.save(new Candidate(
                UUID.randomUUID(),
                request.fullName(),
                request.email(),
                passwordEncoder.encode(request.password())));

        String token = jwtService.issueToken(candidate.getId(), Role.CANDIDATE, candidate.getEmail());
        return new AuthResponse(token, candidate.getId(), Role.CANDIDATE.name(), candidate.getFullName(), candidate.getEmail());
    }
}
