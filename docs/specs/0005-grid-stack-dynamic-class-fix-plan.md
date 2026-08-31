# Implementation Plan: Grid/Stack Dynamic Tailwind Class Fix

**Spec:** [0005-grid-stack-dynamic-class-fix.md](./0005-grid-stack-dynamic-class-fix.md) (Approved)
**Ticket:** Closes #14
**Status:** Approved (Gate 1) — implemented and live-verified 2026-08-31.

## Phase 1: `Grid.tsx`

`frontend/src/components/engine/layout/Grid.tsx`

- [x] Remove `gridCols` (the `` `grid-cols-${columns}` `` class string) and `gapClass` (the `` `gap-${gap}` `` class string) — deleted, along with the now-always-true `Object.keys(style).length > 0` ternary (style always has `gridTemplateColumns`/`gap` set now, so the conditional was dead)
- [x] For `columns: number`: set `style.gridTemplateColumns = \`repeat(${columns}, minmax(0, 1fr))\`` (same pattern already used for numeric `rows`)
- [x] For `gap: number`: set `style.gap = \`${gap * 0.25}rem\`` (matches the existing `columnGap`/`rowGap` conversion in the same file)
- [x] `columns: string` and `gap: string` branches unchanged (already inline-style-based)
- [x] Base `'grid'` class kept in `cn(...)`

## Phase 2: `Stack.tsx`

`frontend/src/components/engine/layout/Stack.tsx`

- [x] Removed `gapClass` (the `` `gap-${spacing}` `` class string) from the `cn(...)` call
- [x] `spacing: number` now sets `style.gap = \`${spacing * 0.25}rem\`` via a single `gapStyle` object covering both the numeric and string cases (simpler than the original two-variable split, same behavior)

## Phase 3: Verification

- [x] `npx tsc --noEmit` — 26 error lines, identical to the pre-existing baseline (no new errors)
- [x] `vite build` — succeeds
- [x] **Live** (real browser, real running stack): Candidate Scorecard (`/candidates/f2d67ae3-...`) — `getComputedStyle` on the `.grid` element showed `grid-template-columns: 476px 476px` (two real tracks) and its two direct children sit at horizontal offsets 280px and 780px (genuinely side-by-side, not stacked)
- [x] **Live**: entity-detail page (`/entities/job_description/615b9875-...`, also `Grid columns:2`) — same `476px 476px` result
- [x] **Live**: Candidate Upload page's outer `Stack spacing:6` — `getComputedStyle(...).gap` = `24px` (6 × 0.25rem × 16px root = 24px, correct conversion), previously `0px`/unset
- [x] **Inspected, not live** — `Grid columns:1` and `columns:6`: no page in this codebase currently uses those values, so there's no existing live surface to exercise them against; `repeat(N, minmax(0, 1fr))` is valid, well-defined CSS Grid syntax for any positive integer N, so this was verified by direct reasoning about the generated string rather than a live DOM check. Flagging this per the repo's testing-transparency requirement rather than silently claiming it was live-tested.
- HMR picked up both file changes automatically on the already-running dev stack (`vite hmr update Stack.tsx`, `Grid.tsx` in the frontend container logs) — no rebuild/restart needed for verification

## Out of scope for this plan (per spec's Non-Goals)

- Auditing/adjusting individual page JSON now that their Grid layout will visually change
- A broader audit of other dynamically-constructed Tailwind classes elsewhere in the codebase

## Out of scope for this plan (per spec's Non-Goals)

- Auditing/adjusting individual page JSON now that their Grid layout will visually change
- A broader audit of other dynamically-constructed Tailwind classes elsewhere in the codebase

## Dependencies between phases

Phase 1 and Phase 2 are independent (different files). Phase 3 depends on both.
