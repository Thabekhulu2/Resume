# Show Scored-Against Job on Candidate History Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-07
**Last Updated:** 2026-09-07
**Ticket:** Closes #24

## Overview

Candidate History (`frontend/src/pages/candidate-history.json`) lists every scored candidate but never shows which job posting they were scored against, even though a `candidate_scored_against_job` relationship already links most of them to a `job_description` entity. Surface that existing link on each row — no new data, no backfill, just displaying what's already recorded.

## Goals

- Each Candidate History row shows the linked job's title (e.g. "Scored against: Graduate Internships") when a `candidate_scored_against_job` relationship exists for that candidate.
- Candidates with no such relationship (older data predating the relationship, or a failed scoring run that never created one) show no job line — not an error, not a placeholder like "undefined".
- No change to which candidates appear, their sort order, status/score display, delete behavior, or the Applications page (spec 0012) — purely an additional read-only field on the existing rows.

## Non-Goals

- Backfilling/inferring a job link for candidates that don't have one — several of today's candidates predate this relationship entirely and there's no reliable way to guess which job they were scored against.
- Editing or re-assigning which job a candidate is linked to.
- Any change to the Applications page or its `recruiter_job_applications` view (that page already shows the job link for applicant-sourced candidates via a different field, `applied_to_job_id`; this ticket is about the separate `candidate_scored_against_job` relationship used by recruiter-uploaded candidates).

## User Stories

### As a recruiter, I want to see which job a candidate was scored against on Candidate History, without opening their scorecard

**Acceptance Criteria:**
- [ ] A candidate with a `candidate_scored_against_job` relationship shows that job's title on its row
- [ ] A candidate with no such relationship shows nothing extra (existing row layout unchanged)
- [ ] Existing sort, status badges, score, View/Delete actions are all unchanged

## Technical Design

### Architecture

```
frontend/src/pages/candidate-history.json
  dataSources.candidates: embed the job via the relationship (confirmed working
  against the live local PostgREST API):
    select: "*, entity_versions(*), entity_facts(*),
              relationships_v2!relationships_v2_child_id_fkey(
                is_current,
                job:entities!relationships_v2_parent_id_fkey(
                  entity_versions(data, is_current)
                )
              )"
  Row: candidate.relationships_v2[0]?.job?.entity_versions[0]?.data?.title,
  if present, renders "Scored against {{title}}" under the existing
  "Uploaded {{date}}" line. Absent -> render nothing (existing layout unchanged).
```

Note: the engine has no per-row nested query support in list pages (confirmed by spec 0012's own view-based workaround for the same class of problem), so this uses a single nested-embed query rather than one query per candidate. `relationships_v2[0]` is whichever row Postgres returns first with no explicit order — acceptable today since no candidate in current data has more than one current `candidate_scored_against_job` relationship; a candidate re-scored against a second job could show either one. Not worth an engine change (foreign-table ordering) for a read-only display nicety — flagged here rather than silently assumed correct.

### Data Model

No schema change. Reuses `relationships_v2` rows of `relationship_type = 'candidate_scored_against_job'` (parent = job entity, child = candidate entity), already written by the scoring workflow.

### Security Considerations

No new tables/views/RLS surface — reuses existing recruiter access to `entities`/`entity_versions`/`relationships_v2` (spec 0008). No candidate-facing exposure (Candidate History is recruiter-only, unchanged).

## Open Questions

- [ ] None — the nested embed was verified against the live local PostgREST API before writing this spec (see Technical Design).
