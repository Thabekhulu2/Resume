package com.resume.backend.decisions;

import com.resume.backend.auth.JwtService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Java equivalent of candidate-notifications.json. Spring Security replaces
// RLS here: every query is explicitly scoped to the caller's own id, since
// there's no database-level policy doing it silently anymore.
@RestController
@PreAuthorize("hasRole('CANDIDATE')")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/api/notifications/mine")
    public List<Notification> mine(@AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(principal.id());
    }

    @PatchMapping("/api/notifications/{id}/read")
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        Notification notification = notificationRepository.findByIdAndRecipientId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
