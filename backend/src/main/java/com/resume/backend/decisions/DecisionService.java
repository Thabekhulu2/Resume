package com.resume.backend.decisions;

import com.resume.backend.core.CoreEntityService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Java port of the Postgres function record_recruiter_decision (migration
// 20260911130000_recruiter_decisions_interviews_notifications.sql) -- same
// guard logic (only notify if the candidate has a self-service applicant_id),
// same job-title resolution, same notification text per decision type.
@Service
public class DecisionService {

    private static final DateTimeFormatter SCHEDULED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final CoreEntityService coreEntityService;
    private final ApplicationDecisionRepository decisionRepository;
    private final InterviewRepository interviewRepository;
    private final NotificationRepository notificationRepository;

    public DecisionService(
            CoreEntityService coreEntityService,
            ApplicationDecisionRepository decisionRepository,
            InterviewRepository interviewRepository,
            NotificationRepository notificationRepository) {
        this.coreEntityService = coreEntityService;
        this.decisionRepository = decisionRepository;
        this.interviewRepository = interviewRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public UUID recordDecision(UUID candidateId, String decision, OffsetDateTime scheduledAt, String notes, UUID createdBy) {
        UUID decisionId = UUID.randomUUID();
        decisionRepository.save(new ApplicationDecision(decisionId, candidateId, decision, notes, createdBy));

        var candidateSnapshot = coreEntityService.getEntity(candidateId);
        Object appliedToJobIdRaw = candidateSnapshot.data().get("applied_to_job_id");
        UUID appliedToJobId = appliedToJobIdRaw != null ? UUID.fromString(appliedToJobIdRaw.toString()) : null;

        if ("interview_scheduled".equals(decision)) {
            interviewRepository.save(new Interview(
                    UUID.randomUUID(), candidateId, appliedToJobId, scheduledAt, notes, createdBy));
        }

        UUID applicantId = candidateEntityApplicantId(candidateId);
        if (applicantId != null) {
            String jobTitle = "the role";
            if (appliedToJobId != null) {
                try {
                    Object title = coreEntityService.getEntity(appliedToJobId).data().get("title");
                    if (title != null) {
                        jobTitle = title.toString();
                    }
                } catch (NoSuchElementException e) {
                    // job entity gone -- fall back to "the role", same as the SQL's coalesce
                }
            }

            String title = notificationTitle(decision);
            String body = notificationBody(decision, jobTitle, scheduledAt);
            notificationRepository.save(new Notification(UUID.randomUUID(), applicantId, title, body));
        }

        return decisionId;
    }

    private UUID candidateEntityApplicantId(UUID candidateId) {
        return coreEntityService.getApplicantId(candidateId);
    }

    private static String notificationTitle(String decision) {
        return switch (decision) {
            case "shortlisted" -> "You've been shortlisted";
            case "interview_scheduled" -> "Interview scheduled";
            case "rejected" -> "Application update";
            default -> throw new IllegalArgumentException("Unknown decision: " + decision);
        };
    }

    private static String notificationBody(String decision, String jobTitle, OffsetDateTime scheduledAt) {
        return switch (decision) {
            case "shortlisted" -> "You have been shortlisted for " + jobTitle + ".";
            case "interview_scheduled" -> "An interview has been scheduled for " + jobTitle + " on "
                    + scheduledAt.format(SCHEDULED_AT_FORMAT) + ".";
            case "rejected" -> "Thank you for applying for " + jobTitle
                    + ". We will not be proceeding with your application at this time.";
            default -> throw new IllegalArgumentException("Unknown decision: " + decision);
        };
    }
}
