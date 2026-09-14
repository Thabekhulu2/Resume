package com.resume.backend.scoring;

public class ScoreResumeFitResult {

    private String candidateEntityId;
    private String versionId;
    private double score;
    private String reasoning;
    private String factId;

    public ScoreResumeFitResult() {
    }

    public String getCandidateEntityId() {
        return candidateEntityId;
    }

    public void setCandidateEntityId(String candidateEntityId) {
        this.candidateEntityId = candidateEntityId;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getFactId() {
        return factId;
    }

    public void setFactId(String factId) {
        this.factId = factId;
    }
}
