package com.resume.backend.scoring;

import java.util.List;
import java.util.Map;

// Java equivalent of temporal/src/activities/scoring.py's ScoreResult dataclass.
public class ScoreResultDto {

    private String name;
    private List<String> skills;
    private List<Map<String, Object>> experience;
    private double score;
    private String reasoning;

    public ScoreResultDto() {
    }

    public ScoreResultDto(String name, List<String> skills, List<Map<String, Object>> experience, double score, String reasoning) {
        this.name = name;
        this.skills = skills;
        this.experience = experience;
        this.score = score;
        this.reasoning = reasoning;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<Map<String, Object>> getExperience() {
        return experience;
    }

    public void setExperience(List<Map<String, Object>> experience) {
        this.experience = experience;
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
}
