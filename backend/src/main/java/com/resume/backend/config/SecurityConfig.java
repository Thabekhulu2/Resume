package com.resume.backend.config;

import com.resume.backend.auth.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Browsers enforce CORS, curl doesn't -- every prior phase's curl-based
    // "live verification" passed even with zero CORS config, since curl never
    // sends the preflight OPTIONS request a real browser does. Only a real
    // browser hitting the Angular dev server (a different origin/port than
    // the API) surfaces this.
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // "/error" must be open too: Spring MVC internally forwards here to
                // render a thrown ResponseStatusException (e.g. our 401s), and that
                // forwarded request re-enters this same filter chain -- without this,
                // an intentional 401 gets clobbered into a 403 by the authorization
                // check on the forwarded request.
                .requestMatchers("/api/health", "/api/auth/**", "/error").permitAll()
                // Everything else requires a valid JWT; per-role restrictions
                // are enforced with @PreAuthorize("hasRole(...)") on individual
                // controller methods (see @EnableMethodSecurity above).
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
