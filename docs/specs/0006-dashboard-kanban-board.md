# Dashboard Kanban Board Specification

**Status:** Approved (revised)
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-31
**Last Updated:** 2026-08-31
**Ticket:** Closes #15

## Revision note

The three-columns-side-by-side layout (originally approved and implemented) stays exactly as is. What changes: each column no longer shows its candidate list inline. Instead, a column shows only its label and count, and is clickable — clicking it navigates to a dedicated page listing that range's candidates. Ticket #15 was never committed, so this is a revision in place, not a new ticket. The data-source design from the original implementation (Phase 1) carries over unchanged; only what each column renders, and a new destination page, change.

## Overview

Dashboard shows three columns side by side (Strong/Potential/Weak), each showing only a label and a live count — no candidate list visible up front. Clicking a column navigates to a new page that lists just that range's candidates, reusing Candidate History's existing row layout.

## Goals

- Three columns, visible side by side, unchanged from the original layout
- Each column shows only its label and count — no candidate names/scores visible on the Dashboard itself
- Clicking a column navigates to a dedicated page (e.g. `/candidates/range/70/100`) listing only that range's candidates, with the same name/score/View-link row layout used elsewhere in the app
- Candidates with no score yet are excluded from all three ranges, same as before

## Non-Goals

- Drag-and-drop (unchanged from original spec)
- Real-time/live-updating counts (unchanged)
- Pagination on the new range-detail page (matches Candidate History's existing no-pagination precedent)
- Any change to Candidate History itself — this is a new, separate, filtered view

## User Stories

### As a reviewer, I want to click a score-range group to see only those candidates on their own page, so the Dashboard stays a quick at-a-glance summary and I only load the detail I actually want

**Acceptance Criteria:**
- [ ] Dashboard shows three columns side by side, each with a label and count, no candidate names visible
- [ ] Clicking a column navigates to a page showing only that range's candidates (name, score, link to scorecard)
- [ ] A candidate with no score yet appears on none of the three range pages
- [ ] The existing "Score a candidate" / "How it works" cards are still present on the Dashboard

## Technical Design

### Architecture

Frontend-only, no schema change.

1. **Dashboard data sources** — unchanged from the original implementation: `strongCandidates`/`potentialCandidates`/`weakCandidates`, each `entities` filtered via inner-joined `entity_facts.value` range. Still used on the Dashboard, but now only for the `.length` count shown on each column — the row-level `entity_versions`/`entity_facts` data these queries also fetch is no longer rendered on the Dashboard itself (still fine to fetch; simplifies reuse of the existing filter logic without a separate count-only query).
2. **New route + page**: `frontend/src/routes/candidates/range/$min/$max.tsx` (file-based route, mirrors the existing `candidates/$id.tsx` pattern) rendering a new `frontend/src/pages/candidate-range.json`.
3. **Dashboard columns become links**: each column `Card` wrapped in (or its clickable area provided by) a `Link` to `/candidates/range/{min}/{max}` with the column's fixed range values (70/100, 50/69, 0/49).

### `candidate-range.json`

- One data source, `candidates`, same shape as the Dashboard's per-bucket sources but with `entity_facts.value` bounds coming from route params: `{ field: "entity_facts.value", op: "gte", value: "{{params.min}}" }` / `op: "lte", value: "{{params.max}}"`
- Row markup (name, score, "View" link) reuses the exact same JSON structure already proven in `candidate-history.json` and the original Kanban implementation — no new rendering logic
- Page heading reflects the range, e.g. `"{{params.min}}-{{params.max}} fit score"`, plus a `Link` back to `/` (Dashboard)
- Empty state: "No candidates in this range." (same pattern as elsewhere)

### Dashboard column changes

- Remove the inline candidate-list `Stack`/`each` block from each column (the part added in the original implementation)
- Each column `Card`'s clickable target: wrap the header (label + count `Badge`) in a `Link` to the range route — reusing the existing `Link` engine component, no new interaction primitive needed
- Column body now only ever shows the label + count; no empty-state text needed there anymore (there's nothing to be empty)

### Security Considerations

None — same as before; the new page reads the same tables via the same `supabase` data source mechanism already in use everywhere else.

### Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Route params (`$min`/`$max`) arrive as strings; filter values need to compare correctly against a numeric column | Low | Low | Already proven safe: Supabase/PostgREST filter values are serialized to query-string text regardless of JS type, so a string `"70"` behaves identically to the numeric `70` literal used in the original implementation — confirm live during the plan phase rather than assuming |
| A `Link` wrapping a `Card` header might not visually read as "clickable" (no hover affordance) unless styled | Low | Medium | Check live; add a `className` (e.g. cursor-pointer / hover state) to the wrapped area if it doesn't already look interactive |

## Testing Strategy

- Live: Dashboard shows three columns with correct counts and no candidate rows
- Live: clicking each column navigates to `/candidates/range/<min>/<max>` and shows exactly the candidates in that range (cross-check against a direct DB query, reusing the boundary-value test data approach from the original implementation)
- Live: a candidate with no score appears on none of the three range pages
- Live: existing Dashboard content ("Score a candidate", "How it works") still renders

## Approval

- [ ] Ndumiso Mpanza
