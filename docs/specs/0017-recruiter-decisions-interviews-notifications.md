# Recruiter Decisions, Scheduled Interviews & Candidate Notifications Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-11
**Last Updated:** 2026-09-11
**Ticket:** Closes #28

## Overview

Redesign the recruiter-facing candidate scorecard (`/candidates/:id`, shown when a recruiter clicks "View") to match a supplied mockup: a 5-stage pipeline stepper, a fit-score panel, an Experience section, an Application status badge, and 4 actions — Shortlist, Schedule Interview, Reject, Close. Add a recruiter "Scheduled Interviews" page. Wire the candidate-facing Notifications page (built as a static placeholder in spec 0016) to real data, so a candidate is actually notified whenever a recruiter takes one of these decision actions. Unlike spec 0016, this requires real backend — none of decision/interview/notification concepts exist in this system yet.

## Goals

- Scorecard shows a 5-stage pipeline stepper (Applied/Screening/Assessment/Interview/Decision), derived from real signals: stage 1 always reached; stage 2 reached once scoring has started (`status` is set); stage 3 once scoring finished (`status` is `scored` or `failed`); stage 4 once the recruiter has clicked Shortlist or Schedule Interview; stage 5 once the recruiter has clicked Reject (final outcome — there is no "Hire" action yet, so stage 5 is only reached via rejection for now).
- Fit-score panel ("Why this score" + matched skills) and Experience section reuse the existing real scoring data (`entity_facts` score/reasoning, `extracted.skills`, `extracted.experience`) — no new scoring/extraction fields.
- Application status badge shows the most recent recruiter decision (Shortlisted / Interview Scheduled / Rejected), or nothing if no decision has been made yet.
- Shortlist and Reject buttons record a decision in one click. Schedule Interview opens a small form (date/time + optional notes) before recording.
- Every decision (shortlist/schedule interview/reject) creates a real notification for that candidate, visible on their real (no longer static) Notifications page.
- A new recruiter sidebar item "Scheduled Interviews" lists every scheduled interview (candidate, job, date/time, status).

## Non-Goals

- No "Hire"/final-offer action — out of scope until requested; stage 5 of the stepper is reached only via Reject for now.
- No new scoring/extraction fields (e.g. no aggregate "years of experience" number, no separate "matched vs. extracted" skill distinction) — the model only ever produced one flat `skills` list and a per-role `experience` array; this spec displays exactly that, restyled.
- No candidate-facing view of the `interviews` table directly — a candidate learns their interview date/time through the notification text, not a live interviews query. (A candidate-side "my interview" view can be a follow-up if wanted.)
- No conversion of the scorecard route into a true modal dialog — it stays the existing full-page route (`/candidates/:id`); the mockup's "X"/"Close" affordances become a single "← Back to Candidate History" link. Flag if literal modal chrome was actually wanted.
- No editing/cancelling a scheduled interview from the new Scheduled Interviews page in v1 — it's a read-only list.
- No email/push notifications — "notified" means visible on the candidate's in-app Notifications page only, consistent with spec 0016's scope.

## User Stories

### As a recruiter, I want to see a candidate's pipeline progress and score at a glance

**Acceptance Criteria:**
- [ ] The 5-stage stepper renders with the correct stages marked done/current based on real `status` and decision data
- [ ] Fit score, "Why this score" reasoning, and matched skills display exactly as today's data already provides
- [ ] Experience section shows the real extracted per-role history
- [ ] Application status badge shows the latest decision, or is absent if none exists yet

### As a recruiter, I want to shortlist or reject a candidate in one click

**Acceptance Criteria:**
- [ ] Clicking Shortlist records a `shortlisted` decision and immediately updates the status badge and stepper
- [ ] Clicking Reject records a `rejected` decision, immediately reflected the same way
- [ ] Both actions create a notification for the candidate

### As a recruiter, I want to schedule an interview with a date/time

**Acceptance Criteria:**
- [ ] Clicking Schedule Interview opens a small form (date/time picker, optional notes)
- [ ] Submitting records an `interview_scheduled` decision, creates an `interviews` row, and creates a notification containing the scheduled date/time
- [ ] The interview then appears on the new Scheduled Interviews page

### As a recruiter, I want a page listing every scheduled interview

**Acceptance Criteria:**
- [ ] New sidebar item "Scheduled Interviews" (recruiter-only, alongside the existing Jobs/Applications/Candidate History/Score a Candidate items)
- [ ] Lists candidate name, job title, scheduled date/time, and status, ordered soonest-first

### As a candidate, I want to be notified when a recruiter makes a decision about my application

**Acceptance Criteria:**
- [ ] The candidate's real Notifications page (spec 0016, currently static) shows a real notification after a recruiter shortlists, schedules an interview for, or rejects them
- [ ] The notification text is specific to the decision (mentions the job; mentions the date/time for a scheduled interview)
- [ ] A candidate only ever sees their own notifications (RLS)

## Technical Design

### Architecture

```
New tables (plain domain tables, NOT the entities/entity_versions SCD2 pattern —
mirrors the existing time_series_points precedent: simple, append-only/updatable,
no version history needed):

application_decisions
  id, candidate_id (-> entities.id), decision ('shortlisted'|'interview_scheduled'|'rejected'),
  notes, created_by (-> recruiters.id), created_at
  -- "current status" = most recent row per candidate_id (latest-wins, same
  -- pattern entity_facts already uses for score)

interviews
  id, candidate_id (-> entities.id), job_id (-> entities.id), scheduled_at,
  status ('scheduled' default), notes, created_by (-> recruiters.id),
  created_at, updated_at

notifications
  id, recipient_id (-> auth.users.id, i.e. entities.applicant_id — the actual
  logged-in person, since one candidate can have multiple `entities` rows,
  one per application, per spec 0016's Profile-page finding),
  title, body, is_read (default false), created_at

New Postgres RPC (no security definer, relies on RLS grants below — same
convention as create_entity_with_version_fn):

record_recruiter_decision(p_candidate_id, p_decision, p_scheduled_at default null, p_notes default null)
  -> inserts application_decisions row
  -> if p_decision = 'interview_scheduled': also inserts interviews row
     (job_id derived from the candidate's current entity_versions.data->>applied_to_job_id)
  -> looks up entities.applicant_id for candidate_id, inserts one notifications
     row addressed to that recipient with decision-specific title/body text
     (job title interpolated from the same applied_to_job_id lookup)
  -> returns the new application_decisions.id

New view: recruiter_scheduled_interviews (security_invoker, mirrors
recruiter_job_applications' shape) — interviews joined to candidate name +
job title, for the new recruiter page.

Frontend:
  frontend/src/pages/candidate-scorecard.json (revised) — stepper, status
    badge, Shortlist/Reject buttons (apiCall rpc record_recruiter_decision),
    Schedule Interview modal (date/time Input + notes Textarea, apiCall rpc)
  frontend/src/pages/candidate-history.json — "View" link unchanged (still
    navigates to /candidates/:id; no modal conversion, per Non-Goals)
  frontend/src/pages/recruiter-interviews.json (new) + new route
  frontend/src/routes/__root.tsx — recruiter Sidebar gains "Scheduled
    Interviews" link
  frontend/src/pages/candidate-notifications.json (revised) — real
    `notifications` data source (recipient scoped by RLS), replacing the
    spec-0016 static placeholder
  frontend/src/components/engine/forms/EngineInput.tsx — `type` union
    extended with `'date' | 'datetime-local'` (native input, no new
    component needed) so Schedule Interview can collect a date/time
```

### Database Schema

```sql
create table application_decisions (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references entities(id) on delete cascade,
  decision text not null check (decision in ('shortlisted','interview_scheduled','rejected')),
  notes text,
  created_by uuid references recruiters(id),
  created_at timestamptz not null default now()
);
create index idx_application_decisions_candidate on application_decisions(candidate_id, created_at desc);

create table interviews (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references entities(id) on delete cascade,
  job_id uuid references entities(id),
  scheduled_at timestamptz not null,
  status text not null default 'scheduled' check (status in ('scheduled','completed','cancelled')),
  notes text,
  created_by uuid references recruiters(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);
create index idx_notifications_recipient on notifications(recipient_id, created_at desc);

alter table application_decisions enable row level security;
alter table interviews enable row level security;
alter table notifications enable row level security;

-- Recruiter-only, mirrors the existing "recruiters full access to X" policies
create policy "recruiters full access to application_decisions" on application_decisions
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "recruiters full access to interviews" on interviews
  for all to authenticated
  using (exists (select 1 from recruiters where id = auth.uid()))
  with check (exists (select 1 from recruiters where id = auth.uid()));

-- Candidates see/update only their own notifications; recruiters (via the RPC,
-- running as themselves) insert. No candidate insert policy -- a candidate
-- can never write their own notifications directly.
create policy "recruiters insert notifications" on notifications
  for insert to authenticated
  with check (exists (select 1 from recruiters where id = auth.uid()));

create policy "candidates select own notifications" on notifications
  for select to authenticated
  using (auth.uid() = recipient_id);

create policy "candidates update own notifications" on notifications
  for update to authenticated
  using (auth.uid() = recipient_id)
  with check (auth.uid() = recipient_id);

grant select, insert on application_decisions to authenticated;
grant select, insert, update on interviews to authenticated;
grant select, insert, update on notifications to authenticated;

create or replace function record_recruiter_decision(
  p_candidate_id uuid,
  p_decision text,
  p_scheduled_at timestamptz default null,
  p_notes text default null
) returns uuid
language plpgsql
as $$
declare
  v_decision_id uuid;
  v_applicant_id uuid;
  v_job_title text;
  v_title text;
  v_body text;
begin
  insert into application_decisions (candidate_id, decision, notes, created_by)
  values (p_candidate_id, p_decision, p_notes, auth.uid())
  returning id into v_decision_id;

  if p_decision = 'interview_scheduled' then
    insert into interviews (candidate_id, job_id, scheduled_at, notes, created_by)
    select p_candidate_id, (cv.data ->> 'applied_to_job_id')::uuid, p_scheduled_at, p_notes, auth.uid()
    from entity_versions cv
    where cv.entity_id = p_candidate_id and cv.is_current;
  end if;

  select c.applicant_id into v_applicant_id from entities c where c.id = p_candidate_id;

  select coalesce(j_ev.data ->> 'title', 'the role') into v_job_title
  from entity_versions cv
  left join entities j on j.id = (cv.data ->> 'applied_to_job_id')::uuid
  left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
  where cv.entity_id = p_candidate_id and cv.is_current;

  if v_applicant_id is not null then
    v_title := case p_decision
      when 'shortlisted' then 'You''ve been shortlisted'
      when 'interview_scheduled' then 'Interview scheduled'
      when 'rejected' then 'Application update'
    end;
    v_body := case p_decision
      when 'shortlisted' then 'You have been shortlisted for ' || v_job_title || '.'
      when 'interview_scheduled' then 'An interview has been scheduled for ' || v_job_title || ' on ' || to_char(p_scheduled_at, 'DD Mon YYYY HH24:MI') || '.'
      when 'rejected' then 'Thank you for applying for ' || v_job_title || '. We will not be proceeding with your application at this time.'
    end;
    insert into notifications (recipient_id, title, body) values (v_applicant_id, v_title, v_body);
  end if;

  return v_decision_id;
end;
$$;

grant execute on function record_recruiter_decision(uuid, text, timestamptz, text) to authenticated;

create view recruiter_scheduled_interviews
  with (security_invoker = true)
  as
select
  i.id,
  i.candidate_id,
  cv.data ->> 'name' as candidate_name,
  i.job_id,
  j_ev.data ->> 'title' as job_title,
  i.scheduled_at,
  i.status,
  i.notes
from interviews i
join entity_versions cv on cv.entity_id = i.candidate_id and cv.is_current
left join entities j on j.id = i.job_id
left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current;

grant select on recruiter_scheduled_interviews to authenticated;
```

### Security Considerations

- `record_recruiter_decision` runs as the caller (no `security definer`), matching the existing `create_entity_with_version_fn` convention — a candidate who somehow called it would have every underlying insert rejected by the tables' recruiter-only RLS policies, so no privilege escalation risk.
- Notifications: candidates can only ever `select`/`update` (mark read) their own row (`recipient_id = auth.uid()`); they have no `insert` policy at all, so a candidate can never fabricate their own notification.
- `interviews`/`application_decisions` stay fully recruiter-only — no candidate policy on either, matching this codebase's established "no policy means denied" convention.

## Open Questions

- [ ] None — pipeline-stage semantics were confirmed with the user before writing this spec; other ambiguities (modal vs. page, no aggregate experience figure) are stated as explicit low-risk assumptions in Non-Goals.
