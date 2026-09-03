# Recruitment Team Jobs Page Specification

**Status:** Draft
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-03
**Last Updated:** 2026-09-03
**Ticket:** Closes #19

## Overview

Give the Recruitment Team a dedicated "Jobs" area: a new nav item, a list of job postings (open by default), and a form to create one. This formalizes the ad-hoc "paste a JD" concept from spec 0001 into a first-class, reusable record — the same one ticket 3's candidate application flow will read as its JD source.

## Goals

- New "Jobs" item in the Recruitment Team sidebar nav
- Jobs list page: shows job postings, open ones by default, with a way to see closed ones too
- Create a job posting: title, description (JD text), location, status
- Close / reopen a posting (status needs to mean something, or "open by default" has no exit)
- Job postings persist as the same entity concept the existing scoring pipeline already understands, so ticket 3 can point candidate applications at them without a data-model change

## Non-Goals

- Candidate-facing application flow (ticket 3, tracked in #19's parent chain)
- Editing a posting's title/description/location after creation (only status changes)
- Approval workflows, multiple recruiters collaborating on one posting, audit trail of edits
- Changing the existing recruiter "paste a JD, upload a resume, score" flow (spec 0001) to require selecting from job postings instead of free-text paste — both flows coexist for now; revisit if it becomes confusing in practice

## User Stories

### As a recruiter, I want to see open job postings so I know what we're hiring for

**Acceptance Criteria:**
- [ ] `/jobs` lists postings with `status = 'open'` by default (title, location, created date)
- [ ] I can toggle to also see closed postings
- [ ] Empty state is a clear "no open jobs" message, not a blank screen

### As a recruiter, I want to create a job posting so candidates (later) and other recruiters can see it

**Acceptance Criteria:**
- [ ] "New Job" opens a form: title (required), description/JD text (required), location (optional)
- [ ] On save, it appears in the open jobs list without a page reload
- [ ] Empty title or description is rejected client-side with an inline message

### As a recruiter, I want to close a posting once it's filled

**Acceptance Criteria:**
- [ ] A "Close" action on an open posting sets it to closed and removes it from the default list view
- [ ] A closed posting can be reopened

## Technical Design

### Architecture

```
Frontend (Jobs page, JSON-engine-driven like existing pages)
  -> Supabase `entities` (entity_type = 'job_description') + `entity_versions`
  -> new RPC create_entity_with_version(p_entity_type, p_data) — atomic entity + v1 insert
  -> status changes: plain `entity_versions` update (no new version row; status isn't
     part of the JD content history worth versioning for v1)
```

### Data Model

No new tables. Reuses `entities.entity_type = 'job_description'` (the same entity type spec 0001 already scores candidates against) — a "job posting" *is* a job_description entity, now created through a dedicated form instead of only ad-hoc during upload.

`entity_versions.data` for `job_description` gains two optional fields (existing rows without them are still valid — the scoring workflow only ever reads `jd_text`):
```json
{
  "title": "...",
  "jd_text": "...",
  "location": "... | null",
  "status": "open | closed"
}
```
- Rows created before this ticket (ad-hoc JDs pasted during a resume upload, per spec 0001) have no `status`/`location` and simply won't appear on the Jobs list — they predate the "posting" concept and were never meant to be managed there. The Jobs list only ever shows entities created through this ticket's create-job flow, which always sets `status` explicitly.
- `relationships_v2`/`entity_facts` usage for scoring is unchanged from spec 0001

### New database function

`create_entity_with_version(p_entity_type text, p_data jsonb) returns uuid` — inserts into `entities`, then `entity_versions` (`version_number = 1`, `is_current = true`), returns the new entity id. Runs as the caller (not `security definer`) — ticket 1's RLS policies already grant authenticated recruiters insert on both tables, so no privilege escalation is needed.

This is a genuine shared dependency, not scope creep: `frontend/src/pages/entity-list.json` (existing, pre-this-ticket) already calls an RPC of this exact name for its "New Entity" button, but the function was never created — that button has been silently broken since the template was scaffolded. This ticket adds the function both projects need.

### UI/UX Design

- New page `frontend/src/pages/jobs.json`, following the existing JSON-driven UI engine pattern (`entity-list.json`, `candidate-upload.json`)
- New route `frontend/src/routes/_authenticated/jobs/index.tsx` (nested under the auth-gated layout from #18, so it's protected automatically)
- Sidebar (`__root.tsx`): new "Jobs" link between "Dashboard" and "Score a Candidate"
- List: card-per-posting, showing title, location, created date, status badge for closed items shown when the closed toggle is on; "Close"/"Reopen" button per card; "New Job" button opens a create modal (mirrors `entity-list.json`'s existing modal pattern)

## Open Questions

- [x] Should `jd_text` support rich text/formatting, or is plain text sufficient for v1? — **Resolved 2026-09-03: plain text.**
