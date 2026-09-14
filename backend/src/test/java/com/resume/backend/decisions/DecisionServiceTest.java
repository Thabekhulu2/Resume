package com.resume.backend.decisions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resume.backend.core.CoreEntityService;
import com.resume.backend.core.EntitySnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecisionServiceTest {

    private CoreEntityService coreEntityService;
    private ApplicationDecisionRepository decisionRepository;
    private InterviewRepository interviewRepository;
    private NotificationRepository notificationRepository;
    private DecisionService service;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        coreEntityService = mock(CoreEntityService.class);
        decisionRepository = mock(ApplicationDecisionRepository.class);
        interviewRepository = mock(InterviewRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        service = new DecisionService(coreEntityService, decisionRepository, interviewRepository, notificationRepository);
    }

    private void candidateHasJob(UUID jobIdOrNull) {
        Map<String, Object> data = jobIdOrNull != null
                ? Map.of("applied_to_job_id", jobIdOrNull.toString())
                : Map.of();
        when(coreEntityService.getEntity(candidateId))
                .thenReturn(new EntitySnapshot(candidateId, "candidate", data, UUID.randomUUID(), 2));
    }

    @Test
    void shortlistedNotifiesTheApplicantWithJobTitle() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(applicantId);
        when(coreEntityService.getEntity(jobId))
                .thenReturn(new EntitySnapshot(jobId, "job_description", Map.of("title", "Backend Engineer"), UUID.randomUUID(), 1));

        service.recordDecision(candidateId, "shortlisted", null, null, recruiterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo(applicantId);
        assertThat(captor.getValue().getTitle()).isEqualTo("You've been shortlisted");
        assertThat(captor.getValue().getBody()).isEqualTo("You have been shortlisted for Backend Engineer.");
        verify(interviewRepository, never()).save(any());
    }

    @Test
    void rejectedProducesTheRejectionNotificationText() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(applicantId);
        when(coreEntityService.getEntity(jobId))
                .thenReturn(new EntitySnapshot(jobId, "job_description", Map.of("title", "Backend Engineer"), UUID.randomUUID(), 1));

        service.recordDecision(candidateId, "rejected", null, null, recruiterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Application update");
        assertThat(captor.getValue().getBody())
                .isEqualTo("Thank you for applying for Backend Engineer. We will not be proceeding with your application at this time.");
    }

    @Test
    void interviewScheduledSavesAnInterviewRowWithTheCandidatesAppliedJob() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(applicantId);
        when(coreEntityService.getEntity(jobId))
                .thenReturn(new EntitySnapshot(jobId, "job_description", Map.of("title", "Backend Engineer"), UUID.randomUUID(), 1));
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-09-20T14:30:00Z");

        service.recordDecision(candidateId, "interview_scheduled", scheduledAt, "bring a laptop", recruiterId);

        ArgumentCaptor<Interview> interviewCaptor = ArgumentCaptor.forClass(Interview.class);
        verify(interviewRepository).save(interviewCaptor.capture());
        assertThat(interviewCaptor.getValue().getCandidateId()).isEqualTo(candidateId);
        assertThat(interviewCaptor.getValue().getJobId()).isEqualTo(jobId);
        assertThat(interviewCaptor.getValue().getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(interviewCaptor.getValue().getNotes()).isEqualTo("bring a laptop");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getTitle()).isEqualTo("Interview scheduled");
        assertThat(notificationCaptor.getValue().getBody())
                .isEqualTo("An interview has been scheduled for Backend Engineer on 20 Sep 2026 14:30.");
    }

    @Test
    void recruiterUploadedCandidateWithNoApplicantIdNeverGetsNotified() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(null);

        service.recordDecision(candidateId, "shortlisted", null, null, recruiterId);

        verify(notificationRepository, never()).save(any());
        // The decision itself must still be recorded regardless of notification eligibility.
        verify(decisionRepository).save(any());
    }

    @Test
    void fallsBackToTheRoleWhenCandidateHasNoAppliedJob() {
        candidateHasJob(null);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(applicantId);

        service.recordDecision(candidateId, "shortlisted", null, null, recruiterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("You have been shortlisted for the role.");
    }

    @Test
    void fallsBackToTheRoleWhenTheJobEntityNoLongerExists() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(applicantId);
        when(coreEntityService.getEntity(jobId)).thenThrow(new NoSuchElementException("gone"));

        service.recordDecision(candidateId, "shortlisted", null, null, recruiterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("You have been shortlisted for the role.");
    }

    @Test
    void decisionIsAlwaysRecordedWithTheGivenNotesAndCreatedBy() {
        candidateHasJob(jobId);
        when(coreEntityService.getApplicantId(candidateId)).thenReturn(null);

        service.recordDecision(candidateId, "rejected", null, "not a fit", recruiterId);

        ArgumentCaptor<ApplicationDecision> captor = ArgumentCaptor.forClass(ApplicationDecision.class);
        verify(decisionRepository).save(captor.capture());
        assertThat(captor.getValue().getCandidateId()).isEqualTo(candidateId);
        assertThat(captor.getValue().getDecision()).isEqualTo("rejected");
        assertThat(captor.getValue().getNotes()).isEqualTo("not a fit");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(recruiterId);
    }
}
