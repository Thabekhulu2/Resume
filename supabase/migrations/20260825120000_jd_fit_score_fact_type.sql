-- Resume -> candidate profile feature (spec: docs/specs/0001-resume-candidate-profile.md)
-- Seeds the fact_type used for the JD fit score. No new tables (ADR-0001).

INSERT INTO fact_types (key, label, description, unit)
VALUES (
  'jd_fit_score',
  'JD Fit Score',
  'LLM-derived score of how well a candidate''s resume fits a given job description, with reasoning stored in entity_facts.metadata',
  'percent'
)
ON CONFLICT (key) DO NOTHING;

-- Private bucket for uploaded resume files.
INSERT INTO storage.buckets (id, name, public)
VALUES ('resumes', 'resumes', false)
ON CONFLICT (id) DO NOTHING;
