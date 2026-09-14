package com.resume.backend.scoring;

// Plain mutable POJO, not a record: Temporal's Jackson-based payload
// converter is safest with getter/setter beans, matching every published
// Temporal Java SDK sample -- mirrors temporal/src/workflows/score_resume_fit/workflow.py's ScoreResumeFitRequest.
public class ScoreResumeFitRequest {

    private String resumeStoragePath;
    private String jobDescriptionEntityId;
    private String jdText;
    private String candidateEntityId;
    private String createdBy;

    public ScoreResumeFitRequest() {
    }

    public String getResumeStoragePath() {
        return resumeStoragePath;
    }

    public void setResumeStoragePath(String resumeStoragePath) {
        this.resumeStoragePath = resumeStoragePath;
    }

    public String getJobDescriptionEntityId() {
        return jobDescriptionEntityId;
    }

    public void setJobDescriptionEntityId(String jobDescriptionEntityId) {
        this.jobDescriptionEntityId = jobDescriptionEntityId;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getCandidateEntityId() {
        return candidateEntityId;
    }

    public void setCandidateEntityId(String candidateEntityId) {
        this.candidateEntityId = candidateEntityId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
