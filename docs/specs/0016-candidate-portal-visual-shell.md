# Candidate Portal Visual Shell Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-11
**Last Updated:** 2026-09-11
**Ticket:** Closes #27

## Overview

Give candidates a full portal experience when they log in, modeled on a clickable HTML prototype (`candidate_portal_clickable_prototype.html`) the user supplied: a sidebar-navigated shell with Dashboard, Apply for Jobs, My Applications, Saved Jobs, Notifications, Messages, My Profile, Job Alerts, and Settings pages, a job-details modal, and a multi-step apply wizard. Per the user's explicit scope choice, this ticket builds the **visual shell only**: every page/nav/modal matching the prototype's structure and look, using placeholder/static data wherever no backend capability exists yet (mirroring the prototype's own hardcoded mock arrays). Wiring each page to real data is out of scope here and will be follow-up tickets.

## Current State

Candidates today get no persistent nav at all — `RoleAwareSidebar` (`frontend/src/routes/__root.tsx`) returns `null` for `role === 'candidate'`, so a logged-in candidate sees only the header and one full-width page, `_candidateAuth/apply/index.tsx` (`candidate-jobs.json`): a list of open jobs, each with an "Apply" button opening a single-step modal (upload résumé → submit → scoring workflow triggers), or an "Applied" badge + résumé link if already applied.

## Goals

- A candidate-specific sidebar shell (matching the prototype's nav items and general visual language — gradient sidebar, section grouping, badge counts) wraps every candidate page, analogous to how `RoleAwareSidebar` already gives recruiters one.
- All 8 prototype pages exist as real routes a candidate can navigate between: Dashboard, Apply for Jobs, My Applications, Saved Jobs, Notifications, Messages, My Profile, Job Alerts, Settings.
- **Apply for Jobs** keeps its existing real functionality (open-jobs list from `candidate_open_jobs`, résumé upload, `start-scoring-workflow` invoke, already-applied branch) — this page is reskinned/restructured to match the prototype's job-card grid + detail modal + 3-step apply wizard, not replaced. The wizard's final "submit" step still performs the real upload-and-invoke; the intermediate review/questions steps are new UI with placeholder content (no new backend to persist answers yet).
- **My Applications** shows the candidate's own applications (real data — reuses/adapts the existing `recruiter_job_applications`-style access pattern scoped to the logged-in candidate) with a status pill and progress-step indicator per row, plus a view-details modal — matching the prototype's page, backed by real (not placeholder) data since it's a straightforward read of data that already exists.
- **Saved Jobs, Notifications, Messages, Job Alerts, Settings** are built visually complete per the prototype (same layout, same interactions — search, toggles, "send message", "create alert", etc. all behave like the prototype's, i.e. `alert(...)`-style acknowledgement or local-only state changes) but backed by `static` data sources (hardcoded arrays in the page JSON) since no underlying tables/features exist yet.
- **My Profile** is visually rebuilt to match the prototype (avatar, personal info fields, professional summary, skills tags, documents list) — reads real candidate data where it already exists (name, résumé), placeholder/static for fields with no backing column yet (phone, professional summary, skills list, additional documents).

## Non-Goals

- Persisting anything new: saved jobs, notifications, sent messages, job alerts, or settings toggles do not survive a page refresh in this ticket — interactions give visual/UI feedback only (consistent with the prototype's own `alert('... (prototype)')` placeholders), and are called out as such in the UI where relevant.
- Any new database tables, columns, or edge functions. This ticket touches only `frontend/`.
- Any change to the recruiter side of the app.
- Editing/saving the Profile page's placeholder fields persistently — the form renders and is interactive but a "Save" action is not wired to persist unbacked fields (matches Non-Goal above).
- Exact pixel-for-pixel CSS parity with the prototype's inline styles. **Assumption** (low-risk, stated per repo's clarification policy rather than blocking on it): this ticket matches the prototype's structure, information architecture, and visual language (colors, spacing, card/badge/progress-step patterns) using this app's existing Tailwind/shadcn design system and the JSON engine's existing component set — not a byte-identical CSS port. Flag if this assumption is wrong.

## User Stories

### As a candidate, I want a persistent sidebar so I can navigate between all portal sections

**Acceptance Criteria:**
- [ ] A candidate-only sidebar renders on every candidate route, with the prototype's 9 nav items (Dashboard, Apply for Jobs, My Applications, Saved Jobs, Notifications [badge count], Messages [badge count], My Profile, Job Alerts, Settings, Logout), grouped the same way (main nav vs. "My account" section)
- [ ] Recruiters are unaffected — `RoleAwareSidebar`'s existing recruiter branch is untouched
- [ ] Clicking a nav item navigates to that page via a real route (TanStack Router), not client-side state swapping like the prototype's `render(page)` — this app already uses real routing everywhere else

### As a candidate, I want a Dashboard summarizing my job search

**Acceptance Criteria:**
- [ ] Shows the prototype's stat tiles (Jobs Applied, Active Applications, Interviews, Shortlisted), an application-progress tracker for one in-flight application, a "Recommended for you" list, and "Recent activity" — all from a `static` data source (hardcoded placeholder numbers/rows) per this ticket's scope

### As a candidate, I want to browse and apply for jobs with the same look as the prototype

**Acceptance Criteria:**
- [ ] Job list renders as cards (icon, title, location, description, skill tags, contract-type tag) with a search input, matching the prototype's `jobbar`/`job` layout — backed by the real `candidate_open_jobs` data source (unchanged query)
- [ ] "View details" opens a modal with full description, responsibilities, and requirements (existing `ModalDefinition`/`openModal` pattern, per `candidate-jobs.json`'s existing modals)
- [ ] "Apply Now" starts a 3-step wizard modal (Review profile → Application questions [placeholder textarea/radio, not persisted] → Review & submit), ending in the existing real upload+invoke submission and a success confirmation, matching the prototype's `showWizard`/`submitApp` flow
- [ ] Already-applied jobs still show the existing "Applied" badge + résumé link branch, unchanged

### As a candidate, I want to see my application history and status

**Acceptance Criteria:**
- [ ] Lists the candidate's own applications with job title, location, applied date, status pill, and a per-row progress-step tracker (Submitted/Screening/Assessment/Interview/Decision), matching the prototype's `pages.applications`
- [ ] "View Application" opens a modal with the same progress tracker + current status + next-step text
- [ ] Backed by real data scoped to the logged-in candidate (adapts the existing applications-view pattern; RLS already restricts a candidate to their own rows per spec 0010/0012)

### As a candidate, I want Saved Jobs, Notifications, Messages, Job Alerts, and Settings pages matching the prototype

**Acceptance Criteria:**
- [ ] Each page matches its prototype counterpart's layout and interactions, backed by a `static` data source seeded with prototype-equivalent placeholder content
- [ ] Interactive elements (Send Message, Create Alert, toggles, Pause) give the same kind of immediate acknowledgement the prototype does (e.g. an inline Alert/toast), clearly not persisted
- [ ] Empty states (e.g. no saved jobs) match the prototype's empty-state pattern

### As a candidate, I want a My Profile page matching the prototype's layout

**Acceptance Criteria:**
- [ ] Avatar, personal info fields, professional summary, skills tags, and documents list render matching the prototype's layout
- [ ] Fields with real backing data (name, résumé/document) show real values; fields with none (phone, summary, additional docs, skills) show placeholder/static content, visually complete but not save-persisted this ticket

## Technical Design

### Architecture

```
frontend/src/routes/_candidateAuth.tsx        -- existing auth guard, gains a shared layout
                                                  (new nested pathless layout route, e.g.
                                                  _candidateAuth/_portal.tsx, rendering the
                                                  candidate sidebar + <Outlet/>) OR extends
                                                  RoleAwareSidebar in __root.tsx to render a
                                                  candidate nav instead of null — pick whichever
                                                  keeps __root.tsx's existing recruiter path
                                                  untouched with the smaller diff (spike first)

frontend/src/routes/_candidateAuth/
  dashboard/index.tsx        -> candidate-dashboard.json
  apply/index.tsx            -> candidate-jobs.json (existing file, revised layout+wizard)
  applications/index.tsx     -> candidate-applications.json (new)
  saved-jobs/index.tsx       -> candidate-saved-jobs.json (new, static)
  notifications/index.tsx    -> candidate-notifications.json (new, static)
  messages/index.tsx         -> candidate-messages.json (new, static)
  profile/index.tsx          -> candidate-profile.json (new, mixed real+static)
  job-alerts/index.tsx       -> candidate-job-alerts.json (new, static)
  settings/index.tsx         -> candidate-settings.json (new, static)
```

### Engine changes needed

1. **Candidate sidebar** — new component (or extend `RoleAwareSidebar`) rendering the prototype's nav list; active-item highlighting via `location.pathname`, same pattern `__root.tsx` already uses for recruiters.
2. **Radio component** — the prototype uses radio groups (SQL-experience question, alert frequency). No `Radio` exists in the registry today (only `Checkbox`). Add `EngineCheckbox`'s sibling `EngineRadio` + registry entry — small, mirrors an existing pattern, matches repo convention of adding minimal reusable primitives when needed (e.g. spec 0012's conditional-filter addition).
3. **Multi-step wizard pattern** — no existing pattern; build from `state.wizardStep` (or similar) + `if`-conditioned content blocks inside the existing modal mechanism, with Back/Continue/Submit buttons driving `setState`. No new action type needed — `setState` + `if` covers it, confirmed against `ActionDispatcher`'s existing capabilities.
4. **`static` data sources** — type already exists (`StaticDataSource`, `types.ts:144-147`) and is implemented in `useDataSources.ts:90-118`, but exercised by zero pages today. This ticket is its first real usage (Dashboard, Saved Jobs, Notifications, Messages, Job Alerts, Settings, and parts of Profile) — verify early (spike, Phase 0) that a page with only `static` sources renders correctly end-to-end before building all six pages against it, since it's untested-in-practice code.
5. No changes to `ColumnDefinition`/table component (unused, out of scope) — list pages use `Stack`/`Card`/`each`, matching every existing page's pattern (e.g. `application-job.json`), not a table.

### Security Considerations

- No new data exposure: `static` sources are inline JSON, not queries — no RLS surface. Real data sources reused here (`candidate_open_jobs`, applications) already enforce candidate-scoped RLS (spec 0008/0010/0012); this ticket doesn't loosen or add access.
- The new sidebar/routes stay under the existing `_candidateAuth` guard — no auth changes.

## Open Questions

- [ ] Sidebar implementation choice (extend `RoleAwareSidebar` vs. new nested layout route) — left to implementation-time spike per Technical Design; either is low-risk and reversible.
- [ ] Confirm the "close visual match, not pixel-identical CSS" assumption in Non-Goals is acceptable, or if exact prototype styling (colors/gradients) should be ported as new Tailwind utilities/CSS instead.
